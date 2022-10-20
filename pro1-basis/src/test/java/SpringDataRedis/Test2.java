package SpringDataRedis;

import cn.sgming.Application;
import cn.sgming.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest(classes = Application.class)
public class Test2 {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper mapper;

    @Test
    public void test2() {
        redisTemplate.opsForHash().put("user:2", "name", "SGMing");
        redisTemplate.opsForHash().put("user:2", "age", "30");

        //获取
        Map<Object, Object> entries = redisTemplate.opsForHash().entries("user:2");
        for (Object o : entries.keySet()) {
            System.out.println(o + ":" + entries.get(o));
        }
    }

    @Test
    public void test1() throws JsonProcessingException {
        //创建对象
        User user = new User(1L,"谌光明", 22);
        //手动序列化
        String json = mapper.writeValueAsString(user);
        //写入数据
        String key = "user:"+user.getId();
        redisTemplate.opsForValue().set(key, json);
        //获取数据
        String result = redisTemplate.opsForValue().get(key);
        //反序列化
        User user1 = mapper.readValue(result, User.class);
        System.out.println(user1);
    }

}
