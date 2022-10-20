package SpringDataRedis;

import cn.sgming.Application;
import cn.sgming.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;


@SpringBootTest(classes = Application.class)
public class Test1 {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    @Test
    public void test1() {
        User user = new User(1L,"谌光明", 22);
        redisTemplate.opsForValue().set("user:" + user.getId(), user);
        System.out.println(redisTemplate.opsForValue().get("user:" + user.getId()));
    }

    @Test
    public void testString() {
        redisTemplate.opsForValue().set("name", "SGMing");
        System.out.println(redisTemplate.opsForValue().get("name"));
    }

}
