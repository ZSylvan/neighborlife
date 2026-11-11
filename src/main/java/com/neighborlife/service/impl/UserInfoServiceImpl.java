package com.neighborlife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.UserInfo;
import com.neighborlife.mapper.UserInfoMapper;
import com.neighborlife.service.IUserInfoService;
import org.springframework.stereotype.Service;







@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
    implements IUserInfoService {

  @Override
  public Result queryUserInfo(Long userId) {
    UserInfo info = getById(userId);
    if (info == null) {
      return Result.ok();
    }
    info.setCreateTime(null);
    info.setUpdateTime(null);
    return Result.ok(info);
  }
}
