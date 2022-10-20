import cn.sgming.factory.JedisConnectionFactory;
import org.junit.Test;
import redis.clients.jedis.Jedis;

public class TestString {
    @Test
    public void test1() {
        Jedis jedis = JedisConnectionFactory.getJedis();
        jedis.select(0);
        jedis.set("name", "SGMing");
        System.out.println(jedis.get("name"));
    }
}
