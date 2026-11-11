package com.neighborlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neighborlife.entity.Voucher;
import java.util.List;
import org.apache.ibatis.annotations.Param;







public interface VoucherMapper extends BaseMapper<Voucher> {

  List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);

  List<Long> selectActiveVoucherIds(@Param("shopId") Long shopId);
}
