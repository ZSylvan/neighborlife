package com.neighborlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.Follow;







public interface IFollowService extends IService<Follow> {

  Result follow(Long followUserId, Boolean isFollow);

  Result isFollow(Long followUserId);

  Result followCommons(Long id);
}
