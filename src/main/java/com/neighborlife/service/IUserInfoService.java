package com.neighborlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.UserInfo;







public interface IUserInfoService extends IService<UserInfo> {

  Result queryUserInfo(Long userId);
}
