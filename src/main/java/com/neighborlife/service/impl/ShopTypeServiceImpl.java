package com.neighborlife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.ShopType;
import com.neighborlife.mapper.ShopTypeMapper;
import com.neighborlife.service.IShopTypeService;
import java.util.List;
import org.springframework.stereotype.Service;







@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType>
    implements IShopTypeService {

  @Override
  public Result queryTypeList() {
    List<ShopType> typeList = query().orderByAsc("sort").list();
    return Result.ok(typeList);
  }
}
