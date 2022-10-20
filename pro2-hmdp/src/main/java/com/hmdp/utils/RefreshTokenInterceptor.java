package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDto;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 拦截所有情况做token刷新的
 */
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取token
        String token = request.getHeader("authorization");
        //token不存在直接放行
        if (token == null) {
            return true;
        }
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        log.info("token刷新拦截器-tokenKey: {}", tokenKey);
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(tokenKey);
        if (userMap.isEmpty()) {//为空处理
            return true;
        }
        //map转对象
        UserDto userDto = BeanUtil.copyProperties(userMap, UserDto.class);
        //刷新token
        redisTemplate.expire(tokenKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //对象存在就记录到ThreadLocal中
        UserDtoHolder.saveUser(userDto);
        log.info("token刷新拦截器-userDto: {}", UserDtoHolder.getUser());
        //不做连接放行所有的请求
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
