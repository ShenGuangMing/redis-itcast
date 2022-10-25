package com.hmdp.service;

import com.hmdp.dto.R;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    R<Object> seckillVouCher(Long voucherId);

    R<Object> createVoucherOrder(Long voucherId, SeckillVoucher voucher);
}
