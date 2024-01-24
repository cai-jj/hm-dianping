package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    //乐观锁解决超卖问题 CAS
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀已经结束！");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            // 库存不足
//            return Result.fail("库存不足！");
//        }
//        //5，扣减库存
//        //使用乐观锁解决超卖问题，只要扣减时的库存和原来查到的库存是一样的，就进行扣减
////        boolean success = seckillVoucherService.update()
////                .setSql("stock= stock -1")
////                .eq("voucher_id", voucherId).
////                        eq("stock", voucher.getStock()).update();
//        //只需要库存大于0即可
//        boolean success = seckillVoucherService.update()
//                .setSql("stock= stock -1")
//                .eq("voucher_id", voucherId).
//                        gt("stock", 0).update();
//        if (!success) {
//            //扣减库存
//            return Result.fail("库存不足！");
//        }
//        //6.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 6.1.订单id
//        RedisIdWorker redisIdWorker = new RedisIdWorker(stringRedisTemplate);
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        // 6.2.用户id
//        Long userId = UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
//        // 6.3.代金券id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        return Result.ok(orderId);
//    }

//    //悲观锁解决一人一单问题 synchronized
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀已经结束！");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            // 库存不足
//            return Result.fail("库存不足！");
//        }
//        //5.创建订单，保证一人一单
//        //存在并发问题，没有创建订单，一个用户发起多个线程同时查询，都满足条件，就会创建多个订单，必须加锁
//        Long userId = UserHolder.getUser().getId();
//        //必须使用intern方法，不然每次都是new String()，锁不一样，只使用userId也不行
//        //使用userId加锁，降低锁粒度
//        synchronized (userId.toString().intern()) {
//            //获取代理对象
//            VoucherOrderServiceImpl currentProxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
//            return currentProxy.createVoucherOrder(voucherId);
//        }
//
//
//    }

//    //使用分布式锁
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀已经结束！");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            // 库存不足
//            return Result.fail("库存不足！");
//        }
//        //5.创建订单，保证一人一单
//        Long userId = UserHolder.getUser().getId();
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(120);
//        try {
//            if(isLock) {
//                log.debug("获得锁");
//                VoucherOrderServiceImpl currentProxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
//                return currentProxy.createVoucherOrder(voucherId);
//            } else {
//                return Result.fail("不允许重复下单");
//            }
//        } finally {
//            lock.unLock();
//        }
//    }

//    //使用redisson分布式锁
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀已经结束！");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            // 库存不足
//            return Result.fail("库存不足！");
//        }
//        //5.创建订单，保证一人一单
//        Long userId = UserHolder.getUser().getId();
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        boolean isLock = lock.tryLock();
//        try {
//            if(isLock) {
//                log.debug("获得锁");
//                VoucherOrderServiceImpl currentProxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
//                return currentProxy.createVoucherOrder(voucherId);
//            } else {
//                return Result.fail("不允许重复下单");
//            }
//        } finally {
//            lock.unlock();
//        }
//    }


    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);


    //开启异步线程执行创建订单操作
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //异步创建订单任务
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    log.debug("阻塞队列获取订单");
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }


    private void handVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户id，不能从ThreadLocal获取，因为这是子线程，代理对象也是拿不到的，因为代理也是基于ThreadLocal
//        Long userId = UserHolder.getUser().getId();
        Long userId = voucherOrder.getUserId();
        //这样拿不到代理对象
//        VoucherOrderServiceImpl currentProxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
        currentProxy.createVoucherOrder1(voucherOrder);

    }

    @Transactional
    public void createVoucherOrder1(VoucherOrder voucherOrder) {
        log.debug("扣减库存，创建订单");
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        save(voucherOrder);
    }

    private VoucherOrderServiceImpl currentProxy = null;
    //异步秒杀进行优化
    //基本思路：将库存和用户id保存到redis中
    // 判断库存是否充足，判断是否一人一单，校验通过后放入阻塞队列，开启异步线程执行队列的创建订单操作
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString());

        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //3.有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 3.1.订单id
        RedisIdWorker redisIdWorker = new RedisIdWorker(stringRedisTemplate);
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 3.2.用户id
        voucherOrder.setUserId(userId);
        // 3.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        //获取代理对象
        currentProxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
        //放入阻塞队列
        orderTasks.add(voucherOrder);
        return Result.ok(orderId);
    }


    //synchronized加在方法上，锁的粒度太大了，每次创建订单都需要加锁，但是我们要求仅仅需要同一个用户创建订单加锁
    //可以减小锁的粒度，使用用户id加锁
    //synchronized不能写在createVoucherOrder方法里面，这样会导致方法的事务还没有提交，锁就释放了，会有并发问题

    //创建订单：扣减库存，创建订单，保证原子性，加入事务
    //直接调用本类加事务的方法，事务会失效，因为事务是基于aop，aop采用生成的代理对象调用事务的方法
    //而本类中调用方法使用的是this，即当前对象
    //解决办法：1.重新构造一个类，将这个方法写到构造的类中，本类注入构造的类，调用方法即可
    //2.使用AopContext获取当前对象的反向代理对象，然后通过反向代理对象执行方法 才可以使事务生效
    //还要在启动类加入@EnableAspectJAutoProxy(exposeProxy = true)来暴露代理对象
    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.订单id
        RedisIdWorker redisIdWorker = new RedisIdWorker(stringRedisTemplate);
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 7.返回订单id
        return Result.ok(orderId);
    }
}
