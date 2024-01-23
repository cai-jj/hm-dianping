package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    //悲观锁解决一人一单问题 synchronized
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        //5.创建订单，保证一人一单
        //存在并发问题，没有创建订单，一个用户发起多个线程同时查询，都满足条件，就会创建多个订单，必须加锁
        Long userId = UserHolder.getUser().getId();
        //必须使用intern方法，不然每次都是new String()，锁不一样，只使用userId也不行
        //使用userId加锁，降低锁粒度
        synchronized (userId.toString().intern()) {
            //获取代理对象
            VoucherOrderServiceImpl currentProxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
            return currentProxy.createVoucherOrder(voucherId);
        }


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
    public  Result createVoucherOrder(Long voucherId) {

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
