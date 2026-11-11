package com.neighborlife.mapper;

import org.apache.ibatis.annotations.Param;

 
public interface CacheCheckpointMapper {

  int saveCheckpoint(
      @Param("destination") String destination,
      @Param("batchId") Long batchId,
      @Param("binlogFile") String binlogFile,
      @Param("binlogOffset") Long binlogOffset,
      @Param("eventTimeMs") Long eventTimeMs);
}
