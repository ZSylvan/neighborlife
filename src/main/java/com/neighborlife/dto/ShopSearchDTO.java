package com.neighborlife.dto;

import lombok.Data;

@Data
public class ShopSearchDTO {
  private Long typeId;
  private String keyword;
  private String area;
  private Long maxPrice;
   
  private Integer minScore;
   
  private Boolean hasVoucher;

  private Double x;
  private Double y;
  private Integer page = 1;
  private Integer size = 10;
   
  private String sort = "score";
}
