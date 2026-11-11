package com.neighborlife.controller;

import com.neighborlife.dto.Result;
import com.neighborlife.service.IShopTypeService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;






@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
  @Resource
  private IShopTypeService typeService;

  @GetMapping("list")
  public Result queryTypeList() {
    return typeService.queryTypeList();
  }
}
