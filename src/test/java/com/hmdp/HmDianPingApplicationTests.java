package com.hmdp;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
