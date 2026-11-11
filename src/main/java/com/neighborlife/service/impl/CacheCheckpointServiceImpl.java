package com.neighborlife.service.impl;

import com.neighborlife.mapper.CacheCheckpointMapper;
import com.neighborlife.service.ICacheCheckpointService;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class CacheCheckpointServiceImpl implements ICacheCheckpointService {
  @Resource
  private CacheCheckpointMapper cacheCheckpointMapper;

  @Override
  public void saveCheckpoint(
      String destination, Long batchId, String binlogFile, Long binlogOffset, Long eventTimeMs) {
    cacheCheckpointMapper.saveCheckpoint(
        destination, batchId, binlogFile, binlogOffset, eventTimeMs);
  }
}
