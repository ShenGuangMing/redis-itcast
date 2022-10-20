import org.junit.After;
import org.junit.Before;
import redis.clients.jedis.Jedis;

public class TestTemplate {
    private Jedis jedis;
    @Before
    public void setUp() {
        jedis = new Jedis("192.168.88.129", 6379);
        jedis.auth("root");
        jedis.select(0);
    }

    @After
    public void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }
}
