package com.neighborlife.entity;

import java.sql.Timestamp;
import lombok.Data;

 
@Data
public class BusinessOutboxTask {
  private Long id;
  private String kind;
  private Long aggregateId;
   
  private Long userId;
   
  private long cursorId;

  private String state;
  private int attempts;
  private int consumeAttempts;
   
  private String resourcePath;
   
  private Timestamp createdAt;
   
  private Timestamp deliverAt;
}
