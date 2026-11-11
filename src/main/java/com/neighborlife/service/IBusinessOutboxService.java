package com.neighborlife.service;

import java.sql.Timestamp;

 
public interface IBusinessOutboxService {
  void appendBusinessEvent(String kind, long aggregateId, Long userId);

  void appendMediaDeletionEvent(String path, long userId);

  void appendShopCacheInvalidation(long shopId);

  void appendOrderExpirationEvent(long orderId, Long userId, Timestamp deadline);

  void relayOutboxBatch();
}
