package com.neighborlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.VoucherOrder;







public interface IVoucherOrderService extends IService<VoucherOrder> {

   
  void persistSeckill(VoucherOrder order);

  Result seckillVoucher(Long voucherId);

  Result orderOrdinaryVoucher(Long voucherId);

  Result queryMyOrder(Long orderId);

  Result queryMyVoucherOrder(Long voucherId);

  Result cancelUnpaidOrdinaryOrder(Long orderId);

  Result payMockOrder(Long orderId, Long expectedPayCents);

  Result queryMyOrders(Integer page, boolean heldOnly);
}
