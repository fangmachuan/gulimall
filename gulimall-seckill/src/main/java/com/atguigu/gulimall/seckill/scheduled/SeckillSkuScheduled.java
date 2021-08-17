package com.atguigu.gulimall.seckill.scheduled;

import com.atguigu.gulimall.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀商品的定时上架功能
 *  每天晚上凌晨3点：在服务器很空闲的时候，来进行上架最近三天秒杀的商品
 *      当天商品00：00：00 - 23：59：59
 *      明天的商品00：00：00 - 23：59：59
 *      后天的商品00：00：00 - 23：59：59
 *
 */
@Slf4j
@Service
public class SeckillSkuScheduled {

    @Autowired
    SeckillService seckillService;

    @Autowired
    RedissonClient redissonClient;

    private  final String  upload_lock = "seckill:upload:lock";  //秒杀商品上架的分布式锁的名字

    //秒杀的幂等性处理，处理重复上架同个商品的问题用redis的分布式锁来解决
    //上架最近三天的秒杀商品
    @Scheduled(cron = "*/3 * * * * ?")  //每天晚上凌晨三点执行
    public void uploadSeckillSkuLatest3Days(){
        //1、重复上架就无需处理了
        log.info("上架秒杀的商品信息。。。。");
        //分布式锁,只要锁执行完成，状态已经更新完成，释放锁以后，其他人获取到就会拿到最新的一个状态，相当于保证了一个原子性
        RLock lock = redissonClient.getLock(upload_lock);
        lock.lock(15, TimeUnit.SECONDS);
        try {
            seckillService.uploadSeckillSkuLatest3Days();
        }finally {
            lock.unlock();
        }
    }

}
