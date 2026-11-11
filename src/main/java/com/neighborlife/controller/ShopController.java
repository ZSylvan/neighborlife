package com.neighborlife.controller;

import com.neighborlife.dto.Result;
import com.neighborlife.dto.ShopSearchDTO;
import com.neighborlife.entity.Shop;
import com.neighborlife.service.IShopService;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;






@RestController
@RequestMapping("/shop")
public class ShopController {

  @Resource

  private IShopService shopService;
  





  @GetMapping("/{id}")
  public Result queryShopById(@PathVariable("id") Long id) {
    return shopService.queryShopDetail(id);
  }

   
  @GetMapping("/search")
  public Result searchShops(ShopSearchDTO criteria) {
    return shopService.searchShops(criteria);
  }

   
  @GetMapping("/catalog")
  public Result catalog(
      @RequestParam(value = "keyword", defaultValue = "") String keyword,
      @RequestParam(value = "typeId", required = false) Long typeId,
      @RequestParam(value = "page", defaultValue = "1") Integer page,
      @RequestParam(value = "size", defaultValue = "12") Integer size,
      @RequestParam(value = "sort", defaultValue = "score") String sort) {
    return shopService.catalog(keyword, typeId, page, size, sort);
  }

   
  @GetMapping("/batch")
  public Result queryShopsByIds(@RequestParam("ids") List<Long> ids) {
    return shopService.queryShopsByIds(ids);
  }

   
  @GetMapping("/areas")
  public Result listAreas() {
    return shopService.listAreas();
  }

  





  @PostMapping
  public Result saveShop(@RequestBody Shop shop) {
    return shopService.saveShop(shop);
  }

  





  @PutMapping
  public Result updateShop(@RequestBody Shop shop) {
     
    return shopService.update(shop);
  }

  






  @GetMapping("/of/type")
  public Result queryShopByType(
      @RequestParam("typeId") Integer typeId,
      @RequestParam(value = "current", defaultValue = "1") Integer current,
      @RequestParam(value = "x", required = false) Double x,
      @RequestParam(value = "y", required = false) Double y) {
    return shopService.queryShopByType(typeId, current, x, y);
  }

  






  @GetMapping("/of/name")
  public Result queryShopByName(
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "current", defaultValue = "1") Integer current) {
    return shopService.queryShopByName(name, current);
  }
}
