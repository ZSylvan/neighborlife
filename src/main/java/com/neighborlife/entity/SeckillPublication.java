package com.neighborlife.entity;

import java.sql.Timestamp;
import lombok.Data;

 
@Data
public class SeckillPublication {
  private Long voucherId;
  private Integer stock;
  private Timestamp beginTime;
  private Timestamp endTime;
  private Integer status;
}
