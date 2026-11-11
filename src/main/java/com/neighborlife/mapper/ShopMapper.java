package com.neighborlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neighborlife.entity.Shop;
import org.apache.ibatis.annotations.Param;







public interface ShopMapper extends BaseMapper<Shop> {

  Long countByShopId(@Param("shopId") Long shopId);
}
