package cn.sgming.util;

import redis.clients.jedis.Jedis;

public class JedisUtil {
    private static final Jedis jedis;
    static {
        //创建Jedis对象给定ip和端口
        jedis = new Jedis("192.168.88.129", 6379);
        //设置密码
        jedis.auth("root");
        //选择库
        jedis.select(0);
    }
    //获取
    public static Jedis getJedis() {
        return jedis;
    }

    //关闭
    public static void closeJedis() {
        if (jedis!= null) {
            jedis.close();
        }
    }
}
