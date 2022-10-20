package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.R;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public R<List<ShopType>> getShopTypeList() {
        //从redis中获取
        String shopTypeListJson = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_PREFIX);
        if (StrUtil.isNotBlank(shopTypeListJson)) {//存在缓存
            //刷新缓存时间
            redisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_PREFIX, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            log.info("shopTypeList 缓存命中");
            //json转对象
            List<ShopType> list = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return R.ok(list);
        }
        //从数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList.size() == 0) {//数据库中未查询到
            return R.error("店铺类型集合为空");
        }
        //转为json
        shopTypeListJson = JSONUtil.toJsonStr(typeList);
        //存入redis中并设置有效时间
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_PREFIX, shopTypeListJson,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return R.ok(typeList);
    }
}
