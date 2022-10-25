package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Data
@Slf4j
public class SimpleRedisLock implements ILock{
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock() {
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        log.info("key: " + KEY_PREFIX + name);
        //获取当前线程id
        String currentThreadId = ID_PREFIX + Thread.currentThread().getId();

        Boolean b = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, currentThreadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }

    /**
     * lua脚本实现释放锁
     */
    @Override
    public void unlock() {
        //调用lua脚本
        redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
                );
    }

//    @Override
//    public void unlock() {
//        String currentThreadId = ID_PREFIX + Thread.currentThread().getId();//当前线程ID
//        String threadId = redisTemplate.opsForValue().get(KEY_PREFIX + name);//获取key对应的线程ID
//        //没有获取到或当前线程id与redis中锁住的线程id不同就返回
//        if (threadId == null || !threadId.equals(currentThreadId)) {
//            return;
//        }
//        redisTemplate.delete(KEY_PREFIX  + name);
//    }
}
