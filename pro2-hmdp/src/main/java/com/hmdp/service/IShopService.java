package com.hmdp.service;

import com.hmdp.dto.R;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    R<Shop> queryShopById(Long id);

    R<Object> updateShop(Shop shop);
}
