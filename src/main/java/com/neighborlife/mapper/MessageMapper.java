package com.neighborlife.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

 
public interface MessageMapper {

  List<Map<String, Object>> selectOrderProgress(
      @Param("orderId") Long orderId, @Param("userId") Long userId);

  List<Map<String, Object>> selectFailures();

  Long countInboxEvent(
      @Param("consumerGroup") String consumerGroup, @Param("eventId") Long eventId);

  int insertInboxEvent(
      @Param("consumerGroup") String consumerGroup, @Param("eventId") Long eventId);

  int insertFailure(
      @Param("consumerGroup") String consumerGroup,
      @Param("eventKey") String eventKey,
      @Param("eventId") Long eventId,
      @Param("brokerMessageId") String brokerMessageId,
      @Param("payload") String payload,
      @Param("reason") String reason);
}
