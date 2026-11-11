package com.neighborlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.Voucher;







public interface IVoucherService extends IService<Voucher> {

  Result queryVoucherOfShop(Long shopId);

  void addSeckillVoucher(Voucher voucher);

  Result capabilities();

  Result createSeckillVoucher(Voucher voucher);

  Result createVoucher(Voucher voucher);
}
