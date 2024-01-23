package com.hmdp;

import cn.hutool.core.date.DateTime;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;
import javafx.util.converter.LocalDateTimeStringConverter;
import org.apache.tomcat.jni.Local;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Test
    void test() {
        save(1L, 10L);
    }

    void save(Long id, Long expireTime) {
        Shop shop = shopService.getById(id);
        RedisData data = new RedisData();
        data.setData(shop);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(data));
    }

    @Test
    void test1() {
        Long id = 1L;
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        System.out.println(redisData);
        String s = JSONUtil.toJsonStr(redisData.getData());
        Shop shop = JSONUtil.toBean(s, Shop.class);
        System.out.println(shop);
    }

    @Test
    void test2() {

        LocalDateTime time = LocalDateTime.of(2024, 1, 1,0, 0,0);

        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);


    }

    @Test
    void testIdWorker() throws InterruptedException {
        RedisIdWorker redisIdWorker = new RedisIdWorker(stringRedisTemplate);
        ExecutorService es = Executors.newFixedThreadPool(500);
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
}
