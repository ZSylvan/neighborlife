package com.neighborlife.service.impl;

import com.neighborlife.mapper.BehaviorMapper;
import com.neighborlife.service.IBehaviorFailureService;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class BehaviorFailureServiceImpl implements IBehaviorFailureService {
  @Resource
  private BehaviorMapper behaviorMapper;

  @Override
  public void saveFailure(
      String topic, Integer partitionId, Long messageOffset, String payload, String reason) {
    behaviorMapper.insertFailure(topic, partitionId, messageOffset, payload, reason);
  }
}
