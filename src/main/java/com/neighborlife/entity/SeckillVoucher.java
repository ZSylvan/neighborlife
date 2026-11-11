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
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {

  private static final long serialVersionUID = 1L;

   
  @TableId(value = "voucher_id", type = IdType.INPUT)
  private Long voucherId;

   
  private Integer stock;

   
  private LocalDateTime createTime;

   
  private LocalDateTime beginTime;

   
  private LocalDateTime endTime;

   
  private LocalDateTime updateTime;
}
