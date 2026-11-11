package com.neighborlife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;





@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {

  private static final long serialVersionUID = 1L;

   
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

   
  private Long userId;

   
  private Long voucherId;

   
  private Long payValueSnapshot;
   
  private String titleSnapshot;
   
  private String rulesSnapshot;
   
  private Integer voucherTypeSnapshot;
   
  private LocalDateTime expireAt;
   
  private Long shopIdSnapshot;

   
  private Integer payType;

   
  private Integer status;

   
  private LocalDateTime createTime;

   
  private LocalDateTime payTime;

   
  private LocalDateTime useTime;

   
  private LocalDateTime refundTime;

   
  private LocalDateTime updateTime;
}
