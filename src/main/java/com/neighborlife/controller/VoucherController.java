package com.neighborlife.controller;

import com.neighborlife.dto.Result;
import com.neighborlife.entity.Voucher;
import com.neighborlife.service.IVoucherService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;






@RestController
@RequestMapping("/voucher")
public class VoucherController {

  @Resource

  private IVoucherService voucherService;

  @GetMapping("/manage/capabilities")
  public Result capabilities() {
    return voucherService.capabilities();
  }

  





  @PostMapping("seckill")
  public Result addSeckillVoucher(@RequestBody Voucher voucher) {
    return voucherService.createSeckillVoucher(voucher);
  }

  





  @PostMapping
  public Result addVoucher(@RequestBody Voucher voucher) {
    return voucherService.createVoucher(voucher);
  }

  





  @GetMapping("/list/{shopId}")
  public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
    return voucherService.queryVoucherOfShop(shopId);
  }
}
