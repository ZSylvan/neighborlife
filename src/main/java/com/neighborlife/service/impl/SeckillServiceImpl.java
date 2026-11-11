package com.neighborlife.service.impl;

import cn.hutool.json.JSONUtil;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.SeckillPublication;
import com.neighborlife.entity.SeckillReservation;
import com.neighborlife.entity.VoucherOrder;
import com.neighborlife.enums.ReservationState;
import com.neighborlife.mapper.BusinessOutboxMapper;
import com.neighborlife.mapper.SeckillReservationMapper;
import com.neighborlife.mapper.SeckillVoucherMapper;
import com.neighborlife.mapper.VoucherOrderMapper;
import com.neighborlife.reliability.RedisScripts;
import com.neighborlife.service.ISeckillService;
import com.neighborlife.service.IVoucherOrderService;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisZSetCommands.Limit;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@DependsOn("flywayInitializer")
public class SeckillServiceImpl implements ISeckillService {

  public static final String STREAM = "stream.orders";
  public static final String GROUP = "g1";

  private static final DefaultRedisScript<Long> STREAM_LAG = RedisScripts.number("stream-lag.lua");
  private static final DefaultRedisScript<Long> COMPENSATE =
      RedisScripts.number("seckill-compensate.lua");

  @Resource

  private BusinessOutboxMapper businessOutboxMapper;

  @Resource

  private SeckillReservationMapper seckillReservationMapper;

  @Resource

  private SeckillVoucherMapper seckillVoucherMapper;

  @Resource

  private VoucherOrderMapper voucherOrderMapper;

  private final String consumer = "worker-" + UUID.randomUUID();
  private final StringRedisTemplate redis;
  private final TransactionTemplate transactions;
  private final RedissonClient redisson;
  private final ObjectProvider<IVoucherOrderService> orders;
  private final MeterRegistry metrics;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "seckill-consumer");
            t.setDaemon(true);
            return t;
          });
  private volatile boolean running;
  private String pendingCursor = "-";
  private final AtomicLong streamLag = new AtomicLong();
  private final AtomicLong reviewSize = new AtomicLong();
  private final AtomicLong pendingSize = new AtomicLong();

  @Value("${neighborlife.reliability.max-deliveries:5}")
  private int maxDeliveries;

  @Value("${neighborlife.reliability.claim-idle-ms:30000}")
  private long idleMillis;

  @Value("${neighborlife.reliability.scheduling-enabled:true}")
  private boolean enabled;

  @Value("${neighborlife.mq.enabled:false}")
  private boolean mqEnabled;

  @Value("${neighborlife.mq.seckill-enabled:false}")
  private boolean mqSeckill;

  public SeckillServiceImpl(
      StringRedisTemplate redis,
      TransactionTemplate transactions,
      RedissonClient redisson,
      ObjectProvider<IVoucherOrderService> orders,
      MeterRegistry metrics) {
    this.redis = redis;
    this.transactions = transactions;
    this.redisson = redisson;
    this.orders = orders;
    this.metrics = metrics;
    metrics.gauge("neighborlife.seckill.pending", pendingSize);
    metrics.gauge("neighborlife.seckill.lag", streamLag);
    metrics.gauge("neighborlife.seckill.review", reviewSize);
  }

  @PostConstruct
  public void start() {
    redis.execute(
        (RedisCallback<Object>)
            connection -> {
              try {
                connection.execute(
                    "XGROUP",
                    bytes("CREATE"),
                    bytes(STREAM),
                    bytes(GROUP),
                    bytes("0"),
                    bytes("MKSTREAM"));
              } catch (RuntimeException e) {
                if (!hasMessage(e, "BUSYGROUP")) {
                  throw e;
                }
              }
              return null;
            });

    for (Map<String, Object> row : seckillVoucherMapper.selectPublicationStates()) {
      long id = ((Number) row.get("voucher_id")).longValue();
      if (row.get("has_published") == null) {
        log.warn("活动 {} 缺少持久化发布基线，保持入口关闭并等待人工核对", id);
        continue;
      }
      if ("ACTIVE".equals(redis.opsForHash().get("seckill:activity:" + id, "state"))) {
        seckillVoucherMapper.markPublished(id);
        continue;
      }
      if (((Number) row.get("has_published")).intValue() == 0
          && !Boolean.TRUE.equals(redis.hasKey("seckill:activity:" + id))
          && seckillReservationMapper.countByVoucherId(id) == 0
          && Boolean.TRUE.equals(redis.opsForStream().size(STREAM) == 0)) {
        publishActivity(id);
      }
    }
    if (enabled) {
      running = true;
      executor.submit(this::consumeOrderStreamMessages);
    }
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static boolean hasMessage(Throwable e, String text) {
    while (e != null) {
      if (e.getMessage() != null && e.getMessage().contains(text)) {
        return true;
      }
      e = e.getCause();
    }
    return false;
  }

  @PreDestroy
  public void stop() {
    running = false;
    executor.shutdownNow();
  }

  private void consumeOrderStreamMessages() {
    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        List<MapRecord<String, Object, Object>> records =
            redis
                .opsForStream()
                .read(
                    Consumer.from(GROUP, consumer),
                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                    StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
        if (records != null) {
          for (MapRecord<String, Object, Object> record : records) {
            handleOrderStreamMessage(record);
          }
        }

        reclaimPending();
        pendingSize.set(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages());
      } catch (RuntimeException e) {
        if (!running) {
          break;
        }
        log.warn("秒杀消费暂时失败，将重试", e);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  @Scheduled(fixedDelay = 10000)
  public void refreshMetrics() {
    pendingSize.set(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages());
    Long lag = redis.execute(STREAM_LAG, Collections.singletonList(STREAM), GROUP);
    streamLag.set(lag == null ? -1 : lag);
    reviewSize.set(seckillReservationMapper.countReviewRequests());
  }

  private void reclaimPending() {
    Range<String> range =
        "-".equals(pendingCursor) ? Range.unbounded() : Range.leftOpen(pendingCursor, "+");
    PendingMessages pending = redis.opsForStream().pending(STREAM, GROUP, range, 50);

    pendingCursor = "-";
    for (PendingMessage message : pending) {
      if (pending.size() == 50) {
        pendingCursor = message.getId().getValue();
      }
      long delay =
          idleMillis * Math.min(16, 1L << Math.min(4, (int) message.getTotalDeliveryCount() - 1));
      if (message.getElapsedTimeSinceLastDelivery().toMillis() < delay) {
        continue;
      }
      for (MapRecord<String, Object, Object> record :
          claim(consumer, Duration.ofMillis(delay), message.getId())) {
        handleOrderStreamMessage(record);
      }
    }
  }

  List<MapRecord<String, Object, Object>> claim(String worker, Duration idle, RecordId id) {
    List<ByteRecord> bytes =
        redis.execute(
            (RedisCallback<List<ByteRecord>>)
                connection ->
                    connection.streamCommands().xClaim(bytes(STREAM), GROUP, worker, idle, id));
    List<MapRecord<String, Object, Object>> records = new ArrayList<>();
    if (bytes != null) {
      for (ByteRecord record : bytes) {
        Map<Object, Object> values = new LinkedHashMap<>();
        record
            .getValue()
            .forEach(
                (key, value) ->
                    values.put(
                        new String(key, StandardCharsets.UTF_8),
                        new String(value, StandardCharsets.UTF_8)));
        records.add(MapRecord.create(STREAM, values).withId(record.getId()));
      }
    }
    return records;
  }

  private void handleOrderStreamMessage(MapRecord<String, Object, Object> record) {
    VoucherOrder order = null;
    boolean reservationMatched = false;
    long began = System.nanoTime();
    try {
      Map<Object, Object> value = record.getValue();
      order =
          new VoucherOrder()
              .setId(parsePositiveLongField(value, "id"))
              .setUserId(parsePositiveLongField(value, "userId"))
              .setVoucherId(parsePositiveLongField(value, "voucherId"));
      registerAndValidateReservation(order);
      reservationMatched = true;
      if (ReservationState.PROCESSING.matches(queryReservationState(order.getId()))) {
        if (mqEnabled && mqSeckill) {
          enqueueFulfillmentTask(order);

          redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
          return;
        } else {
          orders.getObject().persistSeckill(order);
        }
      }

      syncReservationStateAndAcknowledgeMessage(record, order.getId());
    } catch (InvalidOrderMessage e) {

      quarantine(record, order, e);
    } catch (RuntimeException e) {
      metrics.counter("neighborlife.seckill.deliveries", "result", "retry").increment();
      if (reservationMatched) {
        seckillReservationMapper.recordAttempt(summarizeFailureReason(e), order.getId());
        SeckillReservation reservation = seckillReservationMapper.selectAttemptState(order.getId());
        if (reservation != null) {
          String state = reservation.getState();
          if (!ReservationState.PROCESSING.matches(state)) {
            syncReservationStateAndAcknowledgeMessage(record, order.getId());
          } else if (reservation.getAttempts() >= maxDeliveries) {
            final long id = order.getId();
            transactions.execute(
                status -> {
                  seckillReservationMapper.selectStateForUpdate(id);
                  if (seckillReservationMapper.markProcessingForReview(
                          summarizeFailureReason(e), id)
                      > 0) {
                    saveDeadLetter(record, id, e);
                  }
                  return null;
                });

            syncReservationStateAndAcknowledgeMessage(record, id);
          }
        }
      }

      log.warn("订单消息 {} 执行失败: {}", record.getId(), summarizeFailureReason(e));
    } finally {
      metrics
          .timer("neighborlife.seckill.consume")
          .record(System.nanoTime() - began, TimeUnit.NANOSECONDS);
    }
  }

  private void syncReservationStateAndAcknowledgeMessage(
      MapRecord<String, Object, Object> record, long orderId) {
    String state = queryReservationState(orderId);
    redis.opsForHash().put("seckill:request:" + orderId, "state", state);
    redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
    metrics
        .counter("neighborlife.seckill.deliveries", "result", state.toLowerCase(Locale.ROOT))
        .increment();
  }

  private void quarantine(
      MapRecord<String, Object, Object> record, VoucherOrder order, RuntimeException e) {
    transactions.execute(
        status -> {
          saveDeadLetter(record, order == null ? null : order.getId(), e);
          return null;
        });
    redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
    metrics.counter("neighborlife.seckill.deliveries", "result", "invalid").increment();
    log.warn("隔离冲突或无效消息 {}: {}", record.getId(), summarizeFailureReason(e));
  }

  private void saveDeadLetter(
      MapRecord<String, Object, Object> record, Long orderId, RuntimeException e) {
    seckillReservationMapper.insertDeadLetter(
        record.getId().getValue(),
        orderId,
        JSONUtil.toJsonStr(record.getValue()),
        summarizeFailureReason(e));
  }

  private static class InvalidOrderMessage extends RuntimeException {
    InvalidOrderMessage(String message) {
      super(message);
    }
  }

  private static long parsePositiveLongField(Map<Object, Object> fields, String fieldName) {
    Object raw = fields.get(fieldName);
    if (raw == null) {
      throw new InvalidOrderMessage("消息缺少 " + fieldName);
    }
    try {
      long parsed = Long.parseLong(raw.toString());
      if (parsed <= 0) {
        throw new InvalidOrderMessage("消息参数无效");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new InvalidOrderMessage("消息参数无效: " + fieldName);
    }
  }

  private static String summarizeFailureReason(Throwable e) {
    String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    return text.substring(0, Math.min(500, text.length()));
  }

  private void enqueueFulfillmentTask(VoucherOrder order) {
    transactions.execute(
        tx -> {
          businessOutboxMapper.insertSeckillFulfillment(
              order.getId(), order.getUserId(), "seckill:" + order.getId());

          businessOutboxMapper.retryFulfillment("seckill:" + order.getId());
          return null;
        });
    metrics.counter("neighborlife.seckill.handoffs", "result", "durable").increment();
  }

  @Override
  public void fulfillReservationFromMessage(long orderId) {
    SeckillReservation reservation = seckillReservationMapper.selectFulfillment(orderId);
    if (reservation == null) {
      throw new IllegalStateException("缺少持久化抢购资格");
    }
    String state = reservation.getState();
    if (ReservationState.SUCCESS.matches(state) || ReservationState.FAILED.matches(state)) {
      return;
    }
    if (!ReservationState.PROCESSING.matches(state)) {
      throw new IllegalStateException("秒杀请求等待人工核对");
    }
    VoucherOrder order =
        new VoucherOrder()
            .setId(orderId)
            .setUserId(reservation.getUserId())
            .setVoucherId(reservation.getVoucherId());
    orders.getObject().persistSeckill(order);

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              redis
                  .opsForHash()
                  .put("seckill:request:" + orderId, "state", queryReservationState(orderId));
            } catch (RuntimeException unavailable) {
              log.warn("请求 {} 的 Redis 展示状态待修复，数据库履约已提交", orderId);
            }
          }
        });
  }

  private void registerAndValidateReservation(VoucherOrder order) {
    seckillReservationMapper.insertReservation(
        order.getId(), order.getVoucherId(), order.getUserId());
    SeckillReservation reservation = seckillReservationMapper.selectReservation(order.getId());
    if (reservation == null
        || !reservation.getVoucherId().equals(order.getVoucherId())
        || !reservation.getUserId().equals(order.getUserId())) {
      throw new InvalidOrderMessage("秒杀资格或订单号冲突，需核对");
    }
  }

  @Override
  public boolean lockReservationForFulfillment(VoucherOrder order) {
    SeckillReservation reservation = seckillReservationMapper.selectStateForUpdate(order.getId());
    if (reservation == null) {
      throw new IllegalStateException("缺少持久化抢购资格");
    }
    String state = reservation.getState();
    if (ReservationState.FAILED.matches(state) || ReservationState.SUCCESS.matches(state)) {
      return false;
    }
    if (!ReservationState.PROCESSING.matches(state)) {
      throw new IllegalStateException("订单等待人工核对");
    }
    return true;
  }

  @Override
  public void markReservationSuccessful(long orderId) {
    seckillReservationMapper.markSuccess(orderId);
  }

  @Override
  public String queryReservationState(long orderId) {
    return seckillReservationMapper.selectState(orderId);
  }

  @Override
  public Result queryReservationStatus(long orderId, long userId) {
    List<Map<String, Object>> rows = seckillReservationMapper.selectOwnedRequest(orderId, userId);
    if (!rows.isEmpty()) {
      rows.get(0).put("order_id", String.valueOf(orderId));
      return Result.ok(rows.get(0));
    }
    Map<Object, Object> request = redis.opsForHash().entries("seckill:request:" + orderId);
    if (!String.valueOf(userId).equals(request.get("userId"))) {
      return Result.fail("请求不存在");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("order_id", String.valueOf(orderId));
    result.put("voucher_id", request.get("voucherId"));
    result.put("state", request.get("state"));
    return Result.ok(result);
  }

  @Override
  public Result publishActivity(long voucherId) {
    return prepareActivityPublication(voucherId, PublicationMode.INITIAL);
  }

  @Override
  public Result recoverActivity(long voucherId) {
    return prepareActivityPublication(voucherId, PublicationMode.RECOVERY);
  }

  private Result prepareActivityPublication(long voucherId, PublicationMode mode) {
    boolean recovering = mode == PublicationMode.RECOVERY;
    RLock lock = redisson.getLock("lock:publish:" + voucherId);
    if (!lock.tryLock()) {
      return Result.fail("活动正在准备");
    }
    try {
      SeckillPublication activity = seckillVoucherMapper.selectPublicationDetails(voucherId);
      if (activity == null) {
        return Result.fail("活动不存在");
      }
      String activityKey = "seckill:activity:" + voucherId;
      Result admission = checkPublicationAdmission(voucherId, recovering, activityKey);
      if (admission != null) {
        return admission;
      }

      redis.opsForHash().put(activityKey, "state", "PREPARING");
      if (recovering && hasUnfinishedActivityWork(voucherId)) {
        return Result.fail("仍有在途或待核对请求，活动保持暂停，请先处理");
      }
      if (!recovering && seckillReservationMapper.countByVoucherId(voucherId) > 0) {
        return Result.fail("已有抢购记录，请走核对恢复流程");
      }
      rebuildBuyerIndex(voucherId);
      activatePublication(activity, activityKey);
      return Result.ok();
    } finally {
      lock.unlock();
    }
  }

  private Result checkPublicationAdmission(long voucherId, boolean recovering, String activityKey) {
    if (recovering) {
      return null;
    }
    if ("ACTIVE".equals(redis.opsForHash().get(activityKey, "state"))) {
      return Result.ok();
    }
    if (seckillVoucherMapper.selectPublishedFlag(voucherId) == 1) {
      return Result.fail("已发布活动的执行状态缺失，请先核对并恢复");
    }
    long buyers =
        Optional.ofNullable(redis.opsForSet().size("seckill:order:" + voucherId)).orElse(0L);
    long requests =
        Optional.ofNullable(redis.opsForHash().size("seckill:requests:" + voucherId)).orElse(0L);
    if (buyers > 0 || requests > 0) {
      return Result.fail("已有抢购数据，请走核对恢复流程");
    }
    return null;
  }

  private boolean hasUnfinishedActivityWork(long voucherId) {
    PendingMessages pending = redis.opsForStream().pending(STREAM, GROUP, Range.unbounded(), 1);

    boolean unreadMessages = hasUnreadStreamMessages();
    return !pending.isEmpty()
        || unreadMessages
        || seckillReservationMapper.countUnfinishedRequests(voucherId) > 0;
  }

  private boolean hasUnreadStreamMessages() {
    List<StreamInfo.XInfoGroup> groups =
        redis.opsForStream().groups(STREAM).stream().collect(Collectors.toList());
    List<MapRecord<String, Object, Object>> tail =
        redis.opsForStream().reverseRange(STREAM, Range.unbounded(), Limit.limit().count(1));
    if (tail.isEmpty()) {
      return false;
    }
    String tailId = tail.get(0).getId().getValue();
    return groups.stream()
        .anyMatch(
            group -> GROUP.equals(group.groupName()) && !tailId.equals(group.lastDeliveredId()));
  }

  private void rebuildBuyerIndex(long voucherId) {
    List<Map<String, Object>> buyers = seckillReservationMapper.selectSuccessfulBuyers(voucherId);
    redis.delete(Arrays.asList("seckill:order:" + voucherId, "seckill:requests:" + voucherId));
    for (Map<String, Object> buyer : buyers) {
      String userId = buyer.get("user_id").toString();
      redis.opsForSet().add("seckill:order:" + voucherId, userId);
      redis
          .opsForHash()
          .put("seckill:requests:" + voucherId, userId, buyer.get("order_id").toString());
    }
  }

  private void activatePublication(SeckillPublication activity, String activityKey) {
    redis
        .opsForValue()
        .set("seckill:stock:" + activity.getVoucherId(), activity.getStock().toString());
    Map<String, String> config = new HashMap<>();
    config.put("begin", String.valueOf(activity.getBeginTime().getTime()));
    config.put("end", String.valueOf(activity.getEndTime().getTime()));
    config.put("state", Integer.valueOf(1).equals(activity.getStatus()) ? "ACTIVE" : "PAUSED");
    seckillVoucherMapper.markPublished(activity.getVoucherId());
    redis.opsForHash().putAll(activityKey, config);
  }

  @Override
  public Result retryReservation(long orderId) {
    return transactions.execute(
        tx -> {
          SeckillReservation reservation =
              seckillReservationMapper.selectReservationForUpdate(orderId);
          if (reservation == null || !ReservationState.REVIEW.matches(reservation.getState())) {
            return Result.fail("请求不在待核对状态");
          }
          seckillReservationMapper.resetForRetry(orderId);
          businessOutboxMapper.insertSeckillRetry(orderId);
          return Result.ok();
        });
  }

  @Override
  public Result markReservationFailedAndCompensateRedis(long orderId) {
    Result<SeckillReservation> result =
        transactions.execute(
            tx -> {
              SeckillReservation reservation =
                  seckillReservationMapper.selectReservationForUpdate(orderId);
              if (reservation == null) {
                return Result.fail("请求不存在");
              }
              if (!ReservationState.FAILED.matches(reservation.getState())
                  && !ReservationState.REVIEW.matches(reservation.getState())) {
                return Result.fail("仅能处理已隔离的失败请求");
              }
              if (voucherOrderMapper.countByOrderId(orderId) > 0) {
                return Result.fail("订单已经落库，不能回补库存");
              }
              seckillReservationMapper.markFailed(orderId);
              return Result.ok(reservation);
            });
    if (!Boolean.TRUE.equals(result.getSuccess())) {
      return result;
    }
    SeckillReservation reservation = result.getData();
    String voucherIdText = reservation.getVoucherId().toString();
    Long compensationResultCode =
        redis.execute(
            COMPENSATE,
            Arrays.asList(
                "seckill:stock:" + voucherIdText,
                "seckill:order:" + voucherIdText,
                "seckill:requests:" + voucherIdText,
                "seckill:request:" + orderId),
            reservation.getUserId().toString(),
            String.valueOf(orderId));
    if (compensationResultCode == null || compensationResultCode < 0) {
      return Result.fail("失败状态已保存，Redis 尚未补偿，请重试或核对恢复");
    }
    return Result.ok();
  }

  @Override
  public Result queryInventoryReconciliationReport(long voucherId) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("voucher_id", voucherId);
    List<Map<String, Object>> inventoryRows = seckillVoucherMapper.selectInventory(voucherId);
    if (inventoryRows.isEmpty()) {
      return Result.fail("活动不存在");
    }
    long initialStock = ((Number) inventoryRows.get(0).get("total_stock")).longValue();
    long databaseRemainingStock = ((Number) inventoryRows.get(0).get("stock")).longValue();

    long persistedOrderCount = voucherOrderMapper.countByVoucherId(voucherId);
    long unresolvedReservationCount = seckillReservationMapper.countUnfinishedRequests(voucherId);
    String redisRemainingStock = redis.opsForValue().get("seckill:stock:" + voucherId);
    report.put("initial_stock", initialStock);
    report.put("database_remaining", databaseRemainingStock);
    report.put("persisted_orders", persistedOrderCount);
    report.put("database_consistent", initialStock == databaseRemainingStock + persistedOrderCount);
    report.put("unresolved_reservations", unresolvedReservationCount);
    report.put("redis_remaining", redisRemainingStock);
    report.put(
        "redis_difference",
        redisRemainingStock == null
            ? null
            : databaseRemainingStock
                - Long.parseLong(redisRemainingStock)
                - unresolvedReservationCount);
    report.put("note", "差异可能包含尚未被消费者登记的在途消息；本接口只报告，不覆盖库存");
    metrics
        .counter(
            "neighborlife.seckill.reconciliations",
            "result",
            initialStock == databaseRemainingStock + persistedOrderCount
                ? "consistent"
                : "difference")
        .increment();
    return Result.ok(report);
  }

  @Scheduled(fixedDelay = 60000)
  public void checkActivityInventoryConsistency() {
    for (Long id : seckillVoucherMapper.selectActivityVoucherIds()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> report =
            (Map<String, Object>) queryInventoryReconciliationReport(id).getData();
        if (report != null && !Boolean.TRUE.equals(report.get("database_consistent"))) {
          log.warn("秒杀库存对账差异: {}", report);
        }
      } catch (RuntimeException e) {
        log.warn("秒杀对账暂时不可用: {}", id, e);
      }
    }
  }

  @Override
  public Result queryReservationsForReview() {
    return Result.ok(seckillReservationMapper.selectReviewRequests());
  }

  private enum PublicationMode {
    INITIAL,
    RECOVERY
  }
}
