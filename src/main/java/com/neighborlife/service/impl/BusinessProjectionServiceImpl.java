package com.neighborlife.service.impl;

import com.neighborlife.dto.Result;
import com.neighborlife.entity.BusinessOutboxTask;
import com.neighborlife.entity.SeckillReservation;
import com.neighborlife.enums.ReservationState;
import com.neighborlife.mapper.BlogMapper;
import com.neighborlife.mapper.BusinessOutboxMapper;
import com.neighborlife.mapper.FollowMapper;
import com.neighborlife.mapper.MediaMapper;
import com.neighborlife.mapper.SeckillReservationMapper;
import com.neighborlife.mapper.VoucherOrderMapper;
import com.neighborlife.messaging.BusinessEventType;
import com.neighborlife.messaging.ProjectionResult;
import com.neighborlife.reliability.MediaStorage;
import com.neighborlife.service.IBehaviorJournalService;
import com.neighborlife.service.IBusinessProjectionService;
import com.neighborlife.service.IOrderLifecycleService;
import com.neighborlife.service.ISeckillService;
import com.neighborlife.utils.CacheClient;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

 
@Service
public class BusinessProjectionServiceImpl implements IBusinessProjectionService {
  private static final int FEED_BATCH_SIZE = 200;
  private static final int FEED_MAX_ENTRIES = 1000;

  @Resource

  private BlogMapper blogMapper;

  @Resource

  private BusinessOutboxMapper businessOutboxMapper;

  @Resource

  private FollowMapper followMapper;

  @Resource

  private MediaMapper mediaMapper;

  @Resource

  private SeckillReservationMapper seckillReservationMapper;

  @Resource

  private VoucherOrderMapper voucherOrderMapper;

  @Resource

  private ISeckillService coordinator;

  @Resource

  private MediaStorage mediaStorage;

  @Resource

  private ObjectProvider<IOrderLifecycleService> lifecycle;

  @Resource

  private IBehaviorJournalService behavior;

  @Resource

  private RedissonClient redisson;

  private final StringRedisTemplate redis;
  private final CacheClient cache;

  public BusinessProjectionServiceImpl(StringRedisTemplate redis, CacheClient cache) {
    this.redis = redis;
    this.cache = cache;
  }

  @Override
  public ProjectionResult project(BusinessOutboxTask task) {
    BusinessEventType type = BusinessEventType.from(task.getKind());
    if (type != BusinessEventType.LIKE && type != BusinessEventType.FOLLOW) {
      return apply(task, type);
    }
    RLock lock =
        redisson.getLock(
            "lock:projection:"
                + task.getKind()
                + ":"
                + task.getUserId()
                + ":"
                + task.getAggregateId());
    if (!lock.tryLock()) {
      throw new IllegalStateException("关系投影正在处理，稍后重试");
    }
    try {
      return apply(task, type);
    } finally {
      lock.unlock();
    }
  }

  private ProjectionResult apply(BusinessOutboxTask task, BusinessEventType type) {
    switch (type) {
      case ORDER_EXPIRE:
        lifecycle.getObject().closeExpiredOrdinaryOrder(task.getAggregateId());
        break;
      case SECKILL_FULFILL:
        coordinator.fulfillReservationFromMessage(task.getAggregateId());
        break;
      case ORDER_CREATED:
      case ORDER_PAID:
      case ORDER_CLOSED:
        projectOrderEvent(task, type);
        break;
      case MEDIA_DELETE:
        deleteOwnedMedia(task);
        break;
      case SECKILL_PUBLISH:
        publishSeckillActivity(task.getAggregateId());
        break;
      case SECKILL_RETRY:
        enqueueSeckillRetry(task.getAggregateId());
        break;
      case SHOP_INVALIDATE:
        cache.invalidate("cache:shop:" + task.getAggregateId());
        break;
      case FEED:
        return projectFeedBatch(task);
      case FOLLOW:
        projectFollow(task.getUserId(), task.getAggregateId());
        break;
      case LIKE:
        projectLike(task.getUserId(), task.getAggregateId());
        break;
      default:
        throw new IllegalArgumentException("未知 Outbox 类型");
    }
    return ProjectionResult.COMPLETE;
  }

  private void projectOrderEvent(BusinessOutboxTask task, BusinessEventType type) {
    voucherOrderMapper.insertEventHistory(
        task.getId(), task.getAggregateId(), task.getKind(), task.getCreatedAt());
    if (type == BusinessEventType.ORDER_CREATED || type == BusinessEventType.ORDER_PAID) {
      behavior.appendOrderBehaviorEvent(task);
    }
  }

  private void deleteOwnedMedia(BusinessOutboxTask task) {
    String path = MediaStorage.normalize(task.getResourcePath());
    List<String> states = mediaMapper.selectOwnedStates(path, task.getUserId());
    if (states.isEmpty() || "DELETED".equals(states.get(0))) {
      return;
    }
    if (!"DELETING".equals(states.get(0))) {
      throw new IllegalStateException("图片未申请删除，拒绝删除文件");
    }
     
    mediaStorage.delete(path);
    mediaMapper.markDeleted(path);
  }

  private void publishSeckillActivity(long voucherId) {
    Result result = coordinator.publishActivity(voucherId);
    if (!Boolean.TRUE.equals(result.getSuccess())) {
      throw new IllegalStateException(result.getErrorMsg());
    }
  }

  private void enqueueSeckillRetry(long orderId) {
    SeckillReservation reservation = seckillReservationMapper.selectReservation(orderId);
    if (reservation == null) {
      throw new IllegalStateException("抢购请求不存在");
    }
    if (!ReservationState.PROCESSING.matches(reservation.getState())) {
      return;
    }
    Map<String, String> message = new HashMap<>();
    message.put("id", String.valueOf(orderId));
    message.put("userId", reservation.getUserId().toString());
    message.put("voucherId", reservation.getVoucherId().toString());
    redis.opsForStream().add(ISeckillService.STREAM, message);
  }

  private ProjectionResult projectFeedBatch(BusinessOutboxTask task) {
    long cursor = task.getCursorId();
    long blogId = task.getAggregateId();
    double publishedAtMillis = task.getCreatedAt().getTime();
    List<Map<String, Object>> followers =
        followMapper.selectFanBatch(task.getUserId(), cursor, task.getCreatedAt());
    for (Map<String, Object> follower : followers) {
      String feedKey = "feed:" + follower.get("user_id");
      redis.opsForZSet().add(feedKey, String.valueOf(blogId), publishedAtMillis);
      redis.opsForZSet().removeRange(feedKey, 0, -FEED_MAX_ENTRIES - 1);
      cursor = ((Number) follower.get("id")).longValue();
    }
    businessOutboxMapper.updateCursor(cursor, task.getId());
    return followers.size() < FEED_BATCH_SIZE
        ? ProjectionResult.COMPLETE
        : ProjectionResult.CONTINUE;
  }

  private void projectFollow(long userId, long followedUserId) {
     
    String followKey = "follows:" + userId;
    if (followMapper.countEdge(userId, followedUserId) > 0) {
      redis.opsForSet().add(followKey, String.valueOf(followedUserId));
    } else {
      redis.opsForSet().remove(followKey, String.valueOf(followedUserId));
    }
  }

  private void projectLike(long userId, long blogId) {
    List<Map<String, Object>> likes = blogMapper.selectLikeTimestamp(blogId, userId);
    String likedKey = "blog:liked:" + blogId;
    if (likes.isEmpty()) {
      redis.opsForZSet().remove(likedKey, String.valueOf(userId));
    } else {
      Timestamp likedAt = (Timestamp) likes.get(0).get("created_at");
      redis.opsForZSet().add(likedKey, String.valueOf(userId), likedAt.getTime());
    }
  }
}
