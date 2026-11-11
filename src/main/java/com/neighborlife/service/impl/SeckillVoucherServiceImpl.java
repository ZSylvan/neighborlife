package com.neighborlife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neighborlife.entity.SeckillVoucher;
import com.neighborlife.mapper.SeckillVoucherMapper;
import com.neighborlife.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;







@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher>
    implements ISeckillVoucherService {}
