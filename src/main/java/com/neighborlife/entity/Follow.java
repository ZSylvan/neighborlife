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
@TableName("tb_follow")
public class Follow implements Serializable {

  private static final long serialVersionUID = 1L;

   
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

   
  private Long userId;

   
  private Long followUserId;

   
  private LocalDateTime createTime;
}
