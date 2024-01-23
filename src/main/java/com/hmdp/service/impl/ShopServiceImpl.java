package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    @Override
    public Result queryById(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            //redis存在
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//        //判断命中的是否为空值
//        if (shopJson != null) {
//            log.debug("命中空值");
//            return Result.fail("该店铺不存在");
//        }
//        //redis不存在,去数据库里面查
//        Shop shop = shopMapper.selectById(id);
//        if (shop == null) {
//            //数据库也不存在
//            //都不存在，为该key缓存空值，防止缓存穿透
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("该店铺不存在");
//        }
//        //存入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
//        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return Result.ok(shop);

        Shop shop = queryWithMutex(id);
        if(shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //使用互斥锁解决缓存击穿问题
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //redis存在
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        //redis不存在,去数据库里面查
        //只能一个线程去数据库里面查，所以要先获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        Boolean isLock = tryLock(lockKey);
        try {
            if (BooleanUtil.isTrue(isLock)) {
                //获取锁，去数据库里面查
                shop = shopMapper.selectById(id);
                if (shop == null) {
                    //数据库也不存在
                    //都不存在，为该key缓存空值，防止缓存穿透
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                //存入redis
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
                stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return shop;
            } else {
                //获得锁失败，睡眠重试
                Thread.sleep(200);
                return queryWithMutex(id);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            unLock(lockKey);
        }
        return shop;
    }
//    //有bug，查询了很多次数据库
//    public Shop queryWithMutex(Long id)  {
//        String key = CACHE_SHOP_KEY + id;
//        // 1、从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get("key");
//        // 2、判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 存在,直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断命中的值是否是空值
//        if (shopJson != null) {
//            //返回一个错误信息
//            return null;
//        }
//        // 4.实现缓存重构
//        //4.1 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2 判断否获取成功
//            if(!isLock){
//                //4.3 失败，则休眠重试
//                log.debug("获取锁失败，重试");
//                Thread.sleep(200);
//                return queryWithMutex(id);
//            }
//            //4.4 成功，根据id查询数据库
//            shop = getById(id);
//            // 5.不存在，返回错误
//            if(shop == null){
//                //将空值写入redis
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                //返回错误信息
//                return null;
//            }
//            //6.写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
//
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }
//        finally {
//            //7.释放互斥锁
//            unLock(lockKey);
//        }
//        return shop;
//    }

//    利用逻辑过期时间解决缓存击穿问题
    private Shop queryWithLogicTime(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            //没有命中
            return null;
        }
        //判断过期时间是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        String shopJson = JSONUtil.toJsonStr(redisData.getData());
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return shop;
        }
        log.debug("时间过期，重建缓存");
        //已经过期，获取锁缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        if(BooleanUtil.isTrue(isLock)) {
            //获取锁，开启新的线程查询数据库
            new Thread(()->{
                try {
                    Shop newShop = shopMapper.selectById(id);
                    //重建缓存
                    saveShop(id, 20L);
                } finally {
                    unLock(lockKey);
                }

            }).start();
        }
        //返回过期的商铺信息
        return shop;
    }

    Boolean tryLock(String key) {
        //设置过期时间，防止死锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    //查询数据库，重建缓存
    void saveShop(Long id, Long expireTime) {
        Shop shop = shopMapper.selectById(id);
        RedisData data = new RedisData();
        data.setData(shop);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(data));
    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        //先更新数据库，再删除缓存，加入事务保证一致性
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺不存在");
        }
        //更新数据库
        shopMapper.updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
