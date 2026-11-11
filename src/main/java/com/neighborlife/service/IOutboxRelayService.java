package com.neighborlife.service;

import com.neighborlife.entity.BusinessOutboxTask;
import java.util.List;

 
public interface IOutboxRelayService {
  List<BusinessOutboxTask> claimOutboxBatch(String leaseOwner);

  void relayOutboxBatch();

  void redriveUnconsumedEvents();

  void refreshMetrics();
}
