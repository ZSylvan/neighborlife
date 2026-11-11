package com.neighborlife.service;

import com.neighborlife.analytics.BehaviorEvent;
import java.util.List;
import java.util.Map;

 
public interface IBehaviorAggregationService {
  void accept(BehaviorEvent event);

  List<Map<String, Object>> ranking(int minutes, int limit);
}
