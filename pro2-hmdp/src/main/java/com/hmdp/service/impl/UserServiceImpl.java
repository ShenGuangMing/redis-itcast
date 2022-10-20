package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.R;
import com.hmdp.dto.UserDto;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public R<Object> sendCode(String phone, HttpSession session) {
        //校验手机号码
        if (RegexUtils.isPhoneInvalid(phone)) {
            //手机号有误返回
            return R.error("手机号格式有误！");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存到redis中，code两分钟过期，RedisConstants类是记录常量类
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.info("验证码 = {}", code);
        return R.ok();
    }

    @Override
    public R<Object> login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //验证手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //手机号有误返回
            return R.error("手机号格式有误！");
        }
        //生成token，密码登录和电话号登录都使用同一个token
        String token = UUID.randomUUID().toString(true);
        //通过不同登录方式进行验证
        if (loginForm.getCode() != null) {//验证码登录
            return loginByCode(loginForm, session, token);
        }else if (loginForm.getPassword() != null) {//密码登录
            return loginByPassword(loginForm, session, token);
        }
        return R.error("输入的验证码或密码有误");
    }

    private R<Object> loginByPassword(LoginFormDTO loginForm, HttpSession session, String token) {
        //查询手机号是否注册
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, loginForm.getPhone());
        User user = this.getOne(queryWrapper);
        if(user == null) {
            log.info("手机号未注册请先注册！");
            return R.error("手机号未注册请先注册！");
        }
        //处理用户密码为空情况
        if (StrUtil.isBlank(user.getPassword())) {
            log.info("用户密码未设置，请使用短信验证吗登录！");
            return R.error("密码未设置，请使用短信登录！");
        }
        //判断密码是否正确
        if (!user.getPassword().equals(loginForm.getPassword())) {
            return R.error("密码错误！");
        }
        //将用户缓存到redis中
        cacheToRedis(token, user);
        return R.ok();
    }

    private R<Object> loginByCode(LoginFormDTO loginForm, HttpSession session, String token) {
        String phone = loginForm.getPhone();
        //从redis中获取验证码
        String cache = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        log.info("通过验证码登录-Cache: " + cache);
        //缓存验证码不存在了，或验证码有误
        if (cache == null || !cache.equals(loginForm.getCode())) {
            return R.error("验证码有误！");
        }
        //查询该手机号是否注册
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        User user = this.getOne(queryWrapper);
        if (user == null) {//用户不存在进行注册
            user = createUserWithPhone(phone);
        }
        //缓存到redis
        cacheToRedis(token, user);
        //返回token
        return R.ok(token);
    }

    private void cacheToRedis(String token, User user) {
        //将User对象转为Hash存储，如果使用json存储就不会有下面转map问题
        UserDto userDto = BeanUtil.copyProperties(user, UserDto.class);
        //对象转Long，解决Long转换问题
        /*
        这里可以自己实现：new一个User，从map中一个个获取通过key去给对象属性值填充
         */
        Map<String, Object> userMap = BeanUtil.beanToMap(userDto, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreCase(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //给token添加前缀
        String tokenKey = RedisConstants.LOGIN_USER_KEY+ token;
        //存入redis中
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置token有效期
        redisTemplate.expire(tokenKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    private User createUserWithPhone(String phone) {
        //创建User
        User user = new User();
        //设置用户电话号
        user.setPhone(phone);
        //初始化用户nickName
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}
