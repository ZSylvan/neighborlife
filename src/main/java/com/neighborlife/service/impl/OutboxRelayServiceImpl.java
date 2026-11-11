package com.neighborlife.service.impl;

import com.neighborlife.entity.BusinessOutboxTask;
import com.neighborlife.enums.OutboxState;
import com.neighborlife.mapper.BusinessOutboxMapper;
import com.neighborlife.mapper.SeckillReservationMapper;
import com.neighborlife.messaging.BusinessEvent;
import com.neighborlife.messaging.DeliveryRetryPolicy;
import com.neighborlife.messaging.EventPublisher;
import com.neighborlife.service.IBusinessEventService;
import com.neighborlife.service.IOutboxRelayService;
import com.neighborlife.utils.FailureMessages;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class OutboxRelayServiceImpl implements IOutboxRelayService {

  @Resource

  private BusinessOutboxMapper businessOutboxMapper;

  @Resource

  private SeckillReservationMapper seckillReservationMapper;

  private final TransactionTemplate transactions;
  private final ObjectProvider<EventPublisher> publishers;
  private final IBusinessEventService consumer;
  private final MeterRegistry metrics;
  private final AtomicLong pending = new AtomicLong();
  private final AtomicLong review = new AtomicLong();
  private final AtomicLong inflight = new AtomicLong();

  @Value("${neighborlife.mq.enabled:false}")
  private boolean mqEnabled;

  @Value("${neighborlife.mq.lease-seconds:60}")
  private int leaseSeconds;

  @Value("${neighborlife.mq.relay-batch-size:20}")
  private int batchSize;

  @Value("${neighborlife.mq.redelivery-seconds:300}")
  private int redeliverySeconds;

  public OutboxRelayServiceImpl(
      TransactionTemplate transactions,
      ObjectProvider<EventPublisher> publishers,
      IBusinessEventService consumer,
      MeterRegistry metrics) {
    this.transactions = transactions;
    this.publishers = publishers;
    this.consumer = consumer;
    this.metrics = metrics;
    metrics.gauge("neighborlife.outbox.pending", pending);
    metrics.gauge("neighborlife.outbox.review", review);
    metrics.gauge("neighborlife.outbox.inflight", inflight);
  }

  @Override
  public List<BusinessOutboxTask> claimOutboxBatch(String leaseOwner) {
    return transactions.execute(
        tx -> {
          List<BusinessOutboxTask> tasks =
              businessOutboxMapper.selectClaimableEvents(Math.max(1, Math.min(100, batchSize)));
          for (BusinessOutboxTask task : tasks) {
            businessOutboxMapper.claimEvent(leaseOwner, effectiveLeaseSeconds(), task.getId());
          }
          return tasks;
        });
  }

  @Override
  public void relayOutboxBatch() {

    String leaseOwner = UUID.randomUUID().toString();
    for (BusinessOutboxTask task : claimOutboxBatch(leaseOwner)) {
      sendClaimedTask(task, leaseOwner);
    }
  }

  private void sendClaimedTask(BusinessOutboxTask task, String leaseOwner) {
    long started = System.nanoTime();
    try {
      if (businessOutboxMapper.renewLease(effectiveLeaseSeconds(), task.getId(), leaseOwner) == 0) {
        return;
      }
      if (deliverOutboxTask(task, leaseOwner)) {
        metrics.counter("neighborlife.mq.sends", "result", "success").increment();
      }
    } catch (RuntimeException failure) {
      recordSendFailure(task, leaseOwner, failure);
      metrics.counter("neighborlife.mq.sends", "result", "retry").increment();
      log.warn("事件 {} 投递失败，将按原事件号重试: {}", task.getId(), FailureMessages.summarize(failure));
    } finally {
      metrics
          .timer("neighborlife.mq.send")
          .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
  }

  private boolean deliverOutboxTask(BusinessOutboxTask task, String leaseOwner) {
    BusinessEvent event = BusinessEvent.from(task);
    if (mqEnabled) {
      publishers.getObject().publish(event);

      businessOutboxMapper.markSent(task.getId(), leaseOwner);
      return true;
    }
    if (event.getDeliverAt() != null && event.getDeliverAt() > System.currentTimeMillis()) {
      businessOutboxMapper.deferDeadline(task.getId(), leaseOwner);
      return false;
    }
    consumer.consume(event, BusinessEventServiceImpl.resolveConsumerGroup(event.getKind()));
    return true;
  }

  private void recordSendFailure(
      BusinessOutboxTask task, String leaseOwner, RuntimeException failure) {
    int attempts = task.getAttempts() + 1;
    boolean needsReview = attempts >= DeliveryRetryPolicy.MAX_SEND_ATTEMPTS;
    String reason = FailureMessages.summarize(failure);
    transactions.execute(
        tx -> {
          int changed =
              businessOutboxMapper.recordSendFailure(
                  attempts,
                  DeliveryRetryPolicy.delaySeconds(attempts),
                  needsReview ? OutboxState.REVIEW.name() : OutboxState.PENDING.name(),
                  reason,
                  task.getId(),
                  leaseOwner);
          if (changed > 0 && needsReview && "SECKILL_FULFILL".equals(task.getKind())) {
            seckillReservationMapper.markProcessingForReview(reason, task.getAggregateId());
          }
          return null;
        });
  }

  private int effectiveLeaseSeconds() {
    return Math.max(10, leaseSeconds);
  }

  @Scheduled(fixedDelay = 60000)
  @Override
  public void redriveUnconsumedEvents() {
    int age = Math.max(30, redeliverySeconds);

    businessOutboxMapper.redriveUnconsumedEvents(-age, -age);
  }

  @Scheduled(fixedDelay = 10000)
  @Override
  public void refreshMetrics() {
    pending.set(businessOutboxMapper.countPendingEvents());
    review.set(businessOutboxMapper.countReviewEvents());
    inflight.set(businessOutboxMapper.countDueSentEvents());
  }
}
