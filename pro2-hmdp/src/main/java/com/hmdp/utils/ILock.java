package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有时间，超时自动释放锁
     * @return 返回是否获取到锁
     */
    boolean tryLock(long timeoutSec);


    /**
     * 释放锁
     */
    void unlock();
}
