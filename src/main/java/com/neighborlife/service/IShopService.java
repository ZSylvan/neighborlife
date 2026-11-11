package com.neighborlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neighborlife.dto.Result;
import com.neighborlife.dto.ShopSearchDTO;
import com.neighborlife.entity.Shop;
import java.util.List;







public interface IShopService extends IService<Shop> {

  Result queryById(Long id);

  Result update(Shop shop);

  Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

  Result searchShops(ShopSearchDTO criteria);

  Result catalog(String keyword, Long typeId, Integer page, Integer size, String sort);

  Result queryShopsByIds(List<Long> ids);

  Result listAreas();

  Result saveShop(Shop shop);

  Result queryShopByName(String name, Integer current);

  Result queryShopDetail(Long id);
}
