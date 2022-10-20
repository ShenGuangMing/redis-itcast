package cn.sgming.factory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {
    private static final JedisPool jedisPool;
    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        //最大连接
        jedisPoolConfig.setMaxTotal(8);
        //最大空闲连接
        jedisPoolConfig.setMaxIdle(8);
        //最小空闲连接
        jedisPoolConfig.setMinIdle(0);
        //设置最长等待时间
        jedisPoolConfig.setMaxWaitMillis(300);
        jedisPool = new JedisPool(jedisPoolConfig, "192.168.88.129", 6379, 1000, "root");
    }
    //获取Jedis对象
    public static Jedis getJedis() {
        return jedisPool.getResource();
    }

    //获取第几号库的jedis
    public static Jedis getJedis(int index) {
        Jedis jedis = jedisPool.getResource();
        jedis.select(index);
        return jedis;
    }
}
