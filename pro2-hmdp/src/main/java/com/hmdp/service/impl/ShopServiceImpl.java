package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.R;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Override
    public R<Shop> queryShopById(Long id) {
        //缓存穿透是实现
//        Shop shop = cacheClient.queryWithPassThrough(
//                //数据的key和类型
//                RedisConstants.CACHE_SHOP_KEY, id, Shop.class
//                //过期时间与单位
//                , RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES,
//                //fallback
//                this::getById
//        );
        //缓存击穿实现，互斥锁
//        Shop shop = queryWithMutex(id);
        //缓存击穿，逻辑过期实现
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES
        );
        return shop == null ? R.error("店铺id有误") : R.ok(shop);
    }
    private Shop queryWithLogicalExpire(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY+id;
        String redisDataJson = redisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isBlank(redisDataJson)) {//不存在就返回null
            log.info("无效的shopKey");
            return null;
        }
        //缓存命中查反序列化
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //从redisData中获取expireTime
        LocalDateTime expireTime = redisData.getExpireTime();
        //如果未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {//未过期
//            log.info("缓存命中且未过期");
            return shop;
        }
        //过期重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //获取到了锁
        if (isLock) {
            //DoubleCheck，这个线程拿到锁了可能之前有线程做了重建
            //重新获取json
            redisDataJson = redisTemplate.opsForValue().get(shopKey);
            shop = getShopWithRedis(redisDataJson);
            //未过期
            if (shop!= null) {
//                log.info("DoubleCheck缓存命中且未过期");
                return shop;
            }
            //过期重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, RedisConstants.EXPIRE_TIME);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    this.unlock(lockKey);
                }
            });
        }
//        log.info("为获取到锁返回旧值");
        //未获取到锁，返回过期的
        return shop;
    }
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        TimeUnit.MILLISECONDS.sleep(200);
        RedisData redisData = new RedisData();
        //封装数据
        redisData.setData(shop);
        //封装逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 从redis中获取，并判断他的逻辑过期与否
     * @param redisDataJson json
     * @return Shop.class
     */
    private Shop getShopWithRedis(String redisDataJson){
        //缓存命中查反序列化
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //从redisData中获取expireTime
        LocalDateTime expireTime = redisData.getExpireTime();
        //如果未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {//未过期
            return shop;
        }
        //过期返回null
        return null;
    }

    private Shop queryWithMutex(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY+id;
        //从redis中查询缓存
        String shopJson = redisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotBlank(shopJson)) {//查询不为空就直接返回
            log.info("redis缓存命中");
            //命中重新刷新有效期
            redisTemplate.expire(shopKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //shopJson is '/t/n' | "" | null 就会到这里
        if (shopJson != null){//不为null就是空字符
            return null;
        }
        //拼接锁的key
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        Shop shop = null;
        try {
            //获取锁
            boolean isLock = tryLock(lockKey);
            if (!isLock) {//获取锁失败
                //线程睡眠一会重试
                TimeUnit.MILLISECONDS.sleep(50);
                return queryWithMutex(id);
            }
            //获取到了锁就去查数据库
            shop = getById(id);
            //模拟重建时间延时
            TimeUnit.MILLISECONDS.sleep(400);
            if (shop == null) {//不存在就返回错误
                log.info("数据库未查询到");
                //将空值写入redis
                redisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //将对象转为json
            shopJson = JSONUtil.toJsonStr(shop);
            //存入redis,10分钟失效
            redisTemplate.opsForValue().set(shopKey, shopJson, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(lockKey);
        }
        return shop;
    }
    private Shop queryWithPassThrough(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY+id;
        //从redis中查询缓存
        String shopJson = redisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotBlank(shopJson)) {//查询不为空就直接返回
            log.info("redis缓存命中");
            //命中重新刷新有效期
            redisTemplate.expire(shopKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //shopJson is '/t/n' | "" | null 就会到这里
        if (shopJson != null){//不为null就是空字符
            return null;
        }
        //查询数据库
        Shop shop = getById(id);
        if (shop == null) {//不存在就返回错误
            log.info("数据库未查询到");
            //将空值写入redis
            redisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //将对象转为json
        shopJson = JSONUtil.toJsonStr(shop);
        //存入redis,10分钟失效
        redisTemplate.opsForValue().set(shopKey, shopJson, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    @Override
    @Transactional//添加事务
    public R<Object> updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return R.error("ID不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除redis缓存
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+shop.getId());

        return R.ok();
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
