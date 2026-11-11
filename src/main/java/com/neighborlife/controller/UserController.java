package com.neighborlife.controller;

import com.neighborlife.dto.LoginFormDTO;
import com.neighborlife.dto.Result;
import com.neighborlife.dto.UserDTO;
import com.neighborlife.service.IUserInfoService;
import com.neighborlife.service.IUserService;
import com.neighborlife.utils.UserHolder;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;






@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

  @Resource

  private IUserService userService;

  @Resource

  private IUserInfoService userInfoService;

   
  @PostMapping("code")
  public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
     
    return userService.sendCode(phone, session);
  }

  




  @PostMapping("/login")
  public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
     
    return userService.login(loginForm, session);
  }

  




  @PostMapping("/logout")
  public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
    return userService.logout(token);
  }

  @GetMapping("/me")
  public Result me() {
     
    UserDTO user = UserHolder.getUser();
    return Result.ok(user);
  }

  @GetMapping("/info/{id}")
  public Result info(@PathVariable("id") Long userId) {
    return userInfoService.queryUserInfo(userId);
  }

  @GetMapping("/{id}")
  public Result queryUserById(@PathVariable("id") Long userId) {
    return userService.queryUserById(userId);
  }

  @PostMapping("/sign")
  public Result signInToday() {
    return userService.signInToday();
  }

  @GetMapping("/sign/count")
  public Result queryCurrentMonthConsecutiveSignInDays() {
    return userService.queryCurrentMonthConsecutiveSignInDays();
  }
}
