package com.neighborlife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;





@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_info")
public class UserInfo implements Serializable {

  private static final long serialVersionUID = 1L;

   
  @TableId(value = "user_id", type = IdType.AUTO)
  private Long userId;

   
  private String city;

   
  private String introduce;

   
  private Integer fans;

   
  private Integer followee;

   
  private Boolean gender;

   
  private LocalDate birthday;

   
  private Integer credits;

   
  private Boolean level;

   
  private LocalDateTime createTime;

   
  private LocalDateTime updateTime;
}
