package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type,
                                            Function<ID, T> dbFallBack,
                                            Long time, TimeUnit unit
    ) {
        String key = keyPrefix+id;
        String json = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {//不存在就返回null
            log.info("无效的key");
            return null;
        }
        //缓存命中查反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        T t = JSONUtil.toBean(data, type);
        //从redisData中获取expireTime
        LocalDateTime expireTime = redisData.getExpireTime();
        //如果未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {//未过期
            log.info("未过期");
            return t;
        }
        //过期重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //获取到了锁
        if (isLock) {
            //DoubleCheck，这个线程拿到锁了可能之前有线程做了重建
            json = redisTemplate.opsForValue().get(key);
            //重新获取的
            T t1 = getShopWithRedis(json, type);
            if (t1 != null) {
                log.info("DoubleCheck未过期");
                return t1;//返回
            }
            //过期重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //从数据库中获取
                    T t2 = dbFallBack.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key, t2, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    this.unlock(lockKey);
                }
            });
        }
        log.info("未获取到锁返回旧值");
        //未获取到锁，返回过期的
        return t;
    }

    private <T> T getShopWithRedis(String json, Class<T> type) {
        //缓存命中查反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        T t = JSONUtil.toBean(data, type);
        //从redisData中获取expireTime
        LocalDateTime expireTime = redisData.getExpireTime();
        //如果未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {//未过期
            return t;
        }
        //过期返回null
        return null;
    }


    /**
     * 存key-value
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public <T, ID> T queryWithPassThrough(String prefix, ID id, Class<T> type,
                                          Long time, TimeUnit unit,
                                          Function<ID, T> dataFallBack) {
        String key = prefix + id;
        String json = redisTemplate.opsForValue().get(key);
        //如果存在就反序列化并返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        //不存在，去数据库中查询
        T t = dataFallBack.apply(id);
        if (t == null) {
            //存空值
            redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在写入redis
        this.set(key, t, time, unit);
        return t;
    }
    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //不要直接返回flag，因为Boolean是类，可能存在null的情况
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }
}
