package com.neighborlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.ShopType;







public interface IShopTypeService extends IService<ShopType> {

  Result queryTypeList();
}
