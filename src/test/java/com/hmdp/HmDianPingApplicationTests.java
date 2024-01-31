package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;


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

        LocalDateTime time = LocalDateTime.of(2024, 1, 1, 0, 0, 0);

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

    @Test
    void testPath() {
        ClassPathResource classPathResource = new ClassPathResource("unlock.lua");
        System.out.println(classPathResource);
        System.out.println(classPathResource.getFilename());
    }

    @Test
    void testRedisson() {
        RLock lock = redissonClient.getLock("anyLock");

        try {
            boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (isLock) {
                System.out.println("执行...");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            System.out.println("释放锁");
            lock.unlock();
        }
    }

    //存入地理坐标，按照店铺类型进行存放
    @Test
    void loadShopData() {
        List<Shop> shopList = shopService.list();
        //按照店铺类型分类
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            List<Shop> value = entry.getValue();
            for (Shop shop : value) {
                stringRedisTemplate.opsForGeo().add(
                        key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            }
        }
    }

    //测试UV统计
    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("u1", values);
            }
        }
        Long ans = stringRedisTemplate.opsForHyperLogLog().size("u1");
        System.out.println(ans);

    }
}
