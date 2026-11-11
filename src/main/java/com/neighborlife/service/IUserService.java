package com.neighborlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neighborlife.dto.LoginFormDTO;
import com.neighborlife.dto.Result;
import com.neighborlife.entity.User;
import javax.servlet.http.HttpSession;







public interface IUserService extends IService<User> {

  Result sendCode(String phone, HttpSession session);

  Result login(LoginFormDTO loginForm, HttpSession session);

  Result signInToday();

  Result queryCurrentMonthConsecutiveSignInDays();

  Result logout(String token);

  Result queryUserById(Long userId);
}
