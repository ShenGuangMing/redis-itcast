package com.hmdp.utils;


import com.hmdp.dto.UserDto;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(request.getHeader("authorization") == null) {
            return true;
        }
        //在ThreadLocal中获取UserDto
        UserDto user = UserDtoHolder.getUser();
        log.info("登录拦截器-userDto: {}", user);
        if (user == null) {
            //把请求拦截了，并返回
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
