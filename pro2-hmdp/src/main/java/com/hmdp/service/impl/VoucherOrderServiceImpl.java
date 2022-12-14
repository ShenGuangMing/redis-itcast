package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.R;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserDtoHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker idWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public R<Object> seckillVouCher(Long voucherId) {
        //查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        log.info("====voucher: {}", voucher);
        //判断秒杀是否开始
        LocalDateTime nowTime = LocalDateTime.now();
        if (voucher.getBeginTime().isAfter(nowTime)) {//now begin,还没有开始
            return R.error("秒杀还未开始");
        }
        //判断秒杀是否结束
        if (nowTime.isAfter(voucher.getEndTime())) {//end now ，结束
            return R.error("秒杀已经结束");
        }
//        log.info("====stock: {}", voucher.getStock());
        //判断库存是否充足
        if (voucher.getStock() < 1) {//库存不存在
            return R.error("库存不足");
        }
        Long userId = UserDtoHolder.getUser().getId();
        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
        RLock lock = redissonClient.getLock("order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            return R.error("当前用户操作频繁，请稍后重试");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, voucher);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }


    @Transactional
    public R<Object> createVoucherOrder(Long voucherId, SeckillVoucher voucher) {
        Long userId = UserDtoHolder.getUser().getId();
        //一人一单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {//已购买
            return R.error("该优惠券用户已经购买过了");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {//库存扣减失败
            return R.error("库存不足");
        }
        log.info("下订单");
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = idWorker.nextId("order");
        voucherOrder.setId(orderId);//设置订单id
        voucherOrder.setUserId(userId);//设置用户id
        voucherOrder.setVoucherId(voucherId);//设置订单id
        save(voucherOrder);
        //返回订单id
        return R.ok(orderId);
    }
}
