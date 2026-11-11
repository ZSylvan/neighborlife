package com.neighborlife.entity;

import lombok.Data;

 
@Data
public class SeckillReservation {
  private Long orderId;
  private Long voucherId;
  private Long userId;
  private String state;
  private int attempts;
  private String reason;
}
