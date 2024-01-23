package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock {
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    //线程id拼接UUID，因为线程是从0开始计数，不同的JVM可能会线程ID一样，要保证全局唯一
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(flag);
    }

    public void unLock() {
        //1.先判断锁是不是自己的，2.是自己的就删除，而且必须保证两个操作是原子性
        //线程1拿到锁，判断锁是自己的，还没删除(此时发生阻塞，full gc进行垃圾回收)，full gc期间线程1锁超时释放了
        //其他线程获取了锁，当线程1阻塞结束，进行删除锁，此时就会删除其他线程锁
        //使用lua脚本保证两个操作的原子性
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());

//        // 获取线程标示
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        String lockID = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(threadId.equals(lockID)) {
//            //线程id和锁id相同
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }

    }
}
