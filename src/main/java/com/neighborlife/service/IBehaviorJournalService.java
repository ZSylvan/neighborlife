package com.neighborlife.service;

import com.neighborlife.entity.BusinessOutboxTask;

 
public interface IBehaviorJournalService {
  void appendOrderBehaviorEvent(BusinessOutboxTask source);

  void recordShopVisit(long shopId, Long userId);

   
  String submitShopVisitEvent(long shopId, Long userId);

  void relayAnalyticsOutboxBatch();

  void redriveMissingAnalyticsEvents();

  void refreshMetrics();
}
