package com.neighborlife.service;

import com.neighborlife.dto.Result;
import java.util.List;

 
public interface IExperienceService {
  Result queryExperienceCapabilities();

  Result traceShopCacheReads(Long shopId);

  Result submitDemoVisitEvents(Long shopId);

  Result queryVisitEventReceipts(List<String> eventIds);

  Result queryMyOrderProgress(Long orderId);
}
