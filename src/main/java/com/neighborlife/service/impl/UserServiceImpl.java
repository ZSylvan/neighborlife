package com.neighborlife.service.impl;

import static com.neighborlife.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.neighborlife.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.neighborlife.utils.RedisConstants.LOGIN_USER_KEY;
import static com.neighborlife.utils.RedisConstants.LOGIN_USER_TTL;
import static com.neighborlife.utils.RedisConstants.USER_SIGN_KEY;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neighborlife.dto.LoginFormDTO;
import com.neighborlife.dto.Result;
import com.neighborlife.dto.UserDTO;
import com.neighborlife.entity.User;
import com.neighborlife.mapper.UserMapper;
import com.neighborlife.reliability.RateLimiter;
import com.neighborlife.reliability.RedisScripts;
import com.neighborlife.service.IUserService;
import com.neighborlife.utils.RegexUtils;
import com.neighborlife.utils.SystemConstants;
import com.neighborlife.utils.UserHolder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;







@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

  @Resource

  private StringRedisTemplate stringRedisTemplate;

  @Resource

  private RateLimiter rateLimiter;

  private static final DefaultRedisScript<Long> CONSUME_CODE =
      RedisScripts.number("consume-code.lua");

  @Override
  public Result sendCode(String phone, HttpSession session) {
     
    if (RegexUtils.isPhoneInvalid(phone)) {
       
      return Result.fail("手机号格式错误！");
    }
    if (!rateLimiter.allow("code-phone:" + phone, 1, 60000)) {
      return Result.fail("请一分钟后再获取验证码");
    }
     
    String code = RandomUtil.randomNumbers(6);

     
    stringRedisTemplate
        .opsForValue()
        .set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

     
    log.debug("发送短信验证码成功，验证码：{}", code);
     
    return Result.ok();
  }

  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
     
    String phone = loginForm.getPhone();
    if (RegexUtils.isPhoneInvalid(phone)) {
       
      return Result.fail("手机号格式错误！");
    }
    if (!rateLimiter.allow("login-phone:" + phone, 5, 120000)) {
      return Result.fail("尝试次数过多，请稍后重试");
    }
    String code = loginForm.getCode();
    if (code == null
        || !code.matches("[0-9]{6}")
        || !Long.valueOf(1)
            .equals(
                stringRedisTemplate.execute(
                    CONSUME_CODE, Collections.singletonList(LOGIN_CODE_KEY + phone), code))) {
      return Result.fail("验证码错误或已使用");
    }

     
    User user = query().eq("phone", phone).one();

     
    if (user == null) {
       
      try {
        user = createUserWithPhone(phone);
      } catch (DuplicateKeyException duplicate) {
        user = query().eq("phone", phone).one();
      }
    }

     
     
    String token = UUID.randomUUID().toString(true);
     
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    Map<String, Object> userMap =
        BeanUtil.beanToMap(
            userDTO,
            new HashMap<>(),
            CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
     
    String tokenKey = LOGIN_USER_KEY + token;
    stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
     
    stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

     
    return Result.ok(token);
  }

  @Override
  public Result signInToday() {
     
    Long userId = UserHolder.getUser().getId();
     
    LocalDateTime now = LocalDateTime.now();
     
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = USER_SIGN_KEY + userId + keySuffix;
     
    int dayOfMonth = now.getDayOfMonth();
     
    stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
    return Result.ok();
  }

  




  @Override
  public Result queryCurrentMonthConsecutiveSignInDays() {
     
    Long userId = UserHolder.getUser().getId();
     
    LocalDateTime now = LocalDateTime.now();
     
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = USER_SIGN_KEY + userId + keySuffix;
     
    int dayOfMonth = now.getDayOfMonth();
     
    List<Long> bitmapValues =
        stringRedisTemplate
            .opsForValue()
            .bitField(
                key,
                BitFieldSubCommands.create()
                    .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                    .valueAt(0));
    if (bitmapValues == null || bitmapValues.isEmpty()) {
       
      return Result.ok(0);
    }
    Long signInBitmap = bitmapValues.get(0);
    if (signInBitmap == null || signInBitmap == 0) {
      return Result.ok(0);
    }
     
    int consecutiveDays = 0;
    while (true) {
       
      if ((signInBitmap & 1) == 0) {
         
        break;
      } else {
         
        consecutiveDays++;
      }
       
      signInBitmap >>>= 1;
    }
    return Result.ok(consecutiveDays);
  }

  private User createUserWithPhone(String phone) {
     
    User user = new User();
    user.setPhone(phone);
    user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
     
    save(user);
    return user;
  }

  @Override
  public Result logout(String token) {
    if (token == null || token.trim().isEmpty()) {
      return Result.fail("缺少登录令牌");
    }
    stringRedisTemplate.delete(LOGIN_USER_KEY + token);
    UserHolder.removeUser();
    return Result.ok();
  }

  @Override
  public Result queryUserById(Long userId) {
    User user = getById(userId);
    return user == null ? Result.ok() : Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
  }
}
