package com.atguigu.gulimall.seckill.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.seckill.feign.ConponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SecKillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSesssionsWithSkus;
import com.atguigu.gulimall.seckill.vo.SeckillSkuVo;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    ConponFeignService conponFeignService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    RabbitTemplate rabbitTemplate;

    private final String SESSIONS_CACHE_PREFIX = "seckill:sessions:";

    private final String SKUKILL_CACHE_PREFIX = "seckill:skus"; //商品秒杀用的key

    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";//库存信号量+商品随机码

    /**
     * 上架三天的秒杀商品
     */
    //核心代码的幂等性，上架完成后就不用再上架了
    @Override
    public void uploadSeckillSkuLatest3Days() {
        //1。去数据库扫描最近三天哪些商品活动需要参与秒杀的
        R session = conponFeignService.getLaess3DaySession();
        if (session.getCode()==0){ //一切都是成功的状态
            //上架商品
            List<SeckillSesssionsWithSkus> sessionData = session.getData(new TypeReference<List<SeckillSesssionsWithSkus>>() {
            });
            //把最新秒杀的上架商品数据缓存到redis，
            //缓存活动信息
            //先保存活动的信息到redis，比如最近三天都有哪些活动，方便进行检查，保存活动用的key就是开始时间和结束时间
            saveSessionInfos(sessionData);

            //缓存活动关联的商品信息
            //而值就是当前这个活动它里面所有关联的所有商品，就能按照一个活动能查询出它的所有关联的商品
            saveSessionSkuInfos(sessionData);
        }
    }

    //限流降级等操作指定的返回方法
    public  List<SecKillSkuRedisTo> blockHandler(BlockException e){
        log.error("getCurrentSeckillSkusResource被限流了..");
        return null;
    }


    /**
     *  能获取到当前能够秒杀的这些商品信息
     * @return
     */
    @SentinelResource(value = "getCurrentSeckillSkusResource",blockHandler = "blockHandler")
    @Override
    public List<SecKillSkuRedisTo> getCurrentSeckillSkus() {
        //去redis里面查一下当前这个时间要参加秒杀的商品信息
        //1、确定当前时间属于哪个秒杀的场次
        long time = new Date().getTime();//获取到当前时间

        //定义一个受保护的资源
        try(Entry entry = SphU.entry("seckillSkus")) {
            //获取到所有的场次
            Set<String> keys = redisTemplate.keys(SESSIONS_CACHE_PREFIX + "*");
            for (String key : keys) {
                String replace = key.replace(SESSIONS_CACHE_PREFIX, ""); //拿到时间区间
                String[] s = replace.split("_");//分割以后就得到两个区间
                Long start = Long.parseLong(s[0]); //得到开始时间
                Long end =  Long.parseLong(s[1]); //得到结束时间
                //只要当前时间是上面这两个开始时间和结束时间的区间范围
                if (time>=start && time<=end){ //那么说明这个时间的秒杀就是我们需要的场次信息
                    //2.获取这个秒杀场次需要的所有商品信息
                    List<String> range = redisTemplate.opsForList().range(key, -100, 100);
                    //这个有很多商品的值，进行挨个获取
                    BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                    List<String> list = hashOps.multiGet(range);
                    if (list!=null){
                        List<SecKillSkuRedisTo> collect = list.stream().map(item -> {
                            SecKillSkuRedisTo redis = JSON.parseObject((String) item, SecKillSkuRedisTo.class);
                            // redis.setRandomCode(null);//当前秒杀开始了就需要这个随机码
                            return redis;
                        }).collect(Collectors.toList());
                        return collect;
                    }
                    break;
                }
            }
        }catch (BlockException e){
            log.error("资源已被限流了,{}",e.getMessage());
        }

        return null;
    }

    /**
     * 获取某一个sku的秒杀商品信息
     * @param skuId
     * @return
     */
    @Override
    public SecKillSkuRedisTo getSkuSeckillInfo(Long skuId) {
        //1.找到所有需要参与秒杀的商品的key信息
        BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        Set<String> keys = hashOps.keys();
        if(keys!=null&&keys.size()>0){
            String regx = "\\d_" + skuId;
            for (String key : keys) {
                //拿到key来匹配skuid信息，用正则来匹配
                if (Pattern.matches(regx, key)){ //如果匹配上了就是系统秒杀需要的数据
                    String json = hashOps.get(key);
                    SecKillSkuRedisTo skuRedisTo = JSON.parseObject(json, SecKillSkuRedisTo.class);
                    if (skuRedisTo == null) return null;
                    long current = new Date().getTime();
                    if (current>=skuRedisTo.getStartTime() && current<=skuRedisTo.getEndTime()){ //说明在秒杀时间范围内的

                    }else {//如果不在秒杀时间范围内，那么就把随机码至为空
                        skuRedisTo.setRandomCode(null);
                    }
                    return skuRedisTo;
                }

            }
        }
        return null;
    }

    /**
     * 结合了消息队列MQ，此时在这一块倒不是来做最终一致性，虽然也是要保证最终一致，由订单服务要真正的去创建订单，在数据库粒保存
     * 但是此次更多做的是流量的削峰
     * 请求一进来，不用立即去调用订单服务，直接放到MQ消费队列里面，然后让订单服务慢慢去消费就不至于把订单服务打垮
     * @param killId  秒杀用的id
     * @param key 随机码
     * @param num 秒杀几件
     * @return
     */
    @Override
    public String kill(String killId, String key, Integer num) {
        long s1 = System.currentTimeMillis(); //开始时间
        MemberRespVo respVo = LoginUserInterceptor.loginUser.get();//拿到当前登录后用户的所有信息
        //1获取当前秒杀商品的详细信息
        BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        String json = hashOps.get(killId);//获取秒杀单的数据
        if (StringUtils.isEmpty(json)){
            return null;
        }else {
            SecKillSkuRedisTo redis = JSON.parseObject(json, SecKillSkuRedisTo.class);
            //2.合法性其校验性
            //先来检验秒杀时间是不是已经过了
            Long startTime = redis.getStartTime();
            Long endTime = redis.getEndTime();
            long time = new Date().getTime();//当前时间
            long ttl = endTime - time;
            //如果说当前时间在我们的区间范围内
            if(time>=startTime && time<=endTime){ //时间合法可以进行秒杀
                //3.校验随机码和商品的id是否正确
                String randomCode = redis.getRandomCode();
                String skuId = redis.getPromotionSessionId()+"_"+redis.getSkuId();
                if (randomCode.equals(key) && killId.equals(skuId)){ //说明合法的
                    //4. 检验购买秒杀商品的数量是否合理，因为现在都有限制数量
                    if (num<=redis.getSeckillLimit()){ //验证通过
                        //5.验证请求是否已经购买过了，也即秒杀商品都已经买过了，还想再买就不行了,要不然很容易被攻击(也是幂等性处理)
                            //如果只要秒杀成功了，就去redis里面占个位：userId_SessionId+skuid
                        String redisKey = respVo.getId()+"_"+skuId; //当前用户id+场次id+商品id
                        //不存在的时候才占位，最终会返回占位成功还是失败，而且这个占位不是永远占的，只要当前场次一结束，就自动清空
                        //设置自动过期
                        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(redisKey, num.toString(), ttl, TimeUnit.MILLISECONDS);
                        if (aBoolean){ //如果占位成功，说明此时这个请求从来没有买过秒杀的商品
                            //如果以上判断的验证都通过了，那么解下来就可以进行秒杀，但是能秒杀多少件，
                            // 我们系统每一个商品秒杀的件数都按照这个随机码都保存了一个信号数字量
                            //只要正确的进来一个请求，这个信号数字量就减一个
                            //所以就使用分布式信号量来进行操作，能减成功就说明能秒杀成功
                            RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + randomCode);
                                //从信号量中取出一个，但是我们要取几个，就要按照买的数量来
                                //秒杀成功，进行快速下单，发送一个MQ消息
                                boolean b = semaphore.tryAcquire(num);
                                if(b){  //只要信号量成了，才来做接下来的秒杀的逻辑
                                    String timeId = IdWorker.getTimeId();//创建一个订单号
                                    SeckillOrderTo orderTo = new SeckillOrderTo();
                                    orderTo.setOrderSn(timeId);
                                    orderTo.setMemberId(respVo.getId());
                                    orderTo.setNum(num);
                                    orderTo.setPromotionSessionId(redis.getPromotionSessionId());
                                    orderTo.setSkuId(redis.getSkuId());
                                    orderTo.setSeckillPrice(redis.getSeckillPrice());
                                    //把这个订单号发给MQ，然后订单服务进行一个监听
                                    rabbitTemplate.convertAndSend("order-event-exchange", "order.seckill.order", orderTo);

                                    long s2 = System.currentTimeMillis();//结束时间
                                    log.info("逻辑执行完的整体耗时时间【毫秒】...",(s2-s1));
                                    //如果拿到这个订单号，就说明这个秒杀成功了
                                    return timeId;
                                }
                                return null;
                        }else {
                            //占位失败，说明已经买过了,就不能再来购买了
                            return null;
                        }
                    }
                }else {
                    return null;
                }
            }else {
                return null;
            }
        }
        return null;
    }

    /**
     * 保存活动信息
     */
    private void saveSessionInfos(List<SeckillSesssionsWithSkus> sesssions) {
        if (sesssions != null)
            sesssions.stream().forEach(sesssion -> {
                Long startTime = sesssion.getStartTime().getTime();
                Long endTime = sesssion.getEndTime().getTime();
                String key = SESSIONS_CACHE_PREFIX + startTime + "_" + endTime;
                Boolean hasKey = redisTemplate.hasKey(key);//判断一下这个key是不是存在了redis里面了
                if (!hasKey) {//如果没有这个key，就开始进行核心业务处理
                    List<String> collect = sesssion.getRelationSkus().stream().map(item -> item.getPromotionSessionId() + "_" + item.getSkuId().toString()).collect(Collectors.toList());
                    //缓存活动信息
                    redisTemplate.opsForList().leftPushAll(key, collect);
                    //TODO 设置过期时间[已完成]
                   redisTemplate.expireAt(key, new Date(endTime));
                }
            });
    }

    /**
     * 保存活动相关的商品信息
     */
    private void saveSessionSkuInfos(List<SeckillSesssionsWithSkus> sesssions){
        if(sesssions!=null)
        sesssions.stream().forEach(sesssion ->{
            BoundHashOperations<String, Object, Object> ops = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
            sesssion.getRelationSkus().stream().forEach(seckillSkuVo -> {
                //3.每一个商品要秒杀，就要一个随机码，这个随机码的作用就是防止被攻击，只要秒杀一开放，如果不加随机值，那么立马请求发出去，肯定就第一个抢到，
                //所以引入随机码的目的就是想要来参加秒杀，若不知道这个随机码，那么就发请求也没什么用，这个随机码只有在秒杀开启的那一刻才暴露出来
                //所以要为每一个商品设置上秒杀随机码
                String token = UUID.randomUUID().toString().replace("-", "");
                if (!ops.hasKey(seckillSkuVo.getPromotionSessionId().toString()+"_"+seckillSkuVo.getSkuId().toString())){
                    //缓存商品信息
                    SecKillSkuRedisTo redisTo = new SecKillSkuRedisTo();
                    //1.缓存sku的基本信息数据 ,也即放到页面展示的，这样查起来方便
                    R skuInfo = productFeignService.getSkuInfo(seckillSkuVo.getSkuId());
                    if (skuInfo.getCode() == 0){
                        SkuInfoVo info = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                        });
                        redisTo.setSkuInfo(info);
                    }
                    //2.sku的秒杀信息，比如秒杀的价格以及一些秒杀数量等
                    BeanUtils.copyProperties(seckillSkuVo,redisTo);
                    redisTo.setRandomCode(token);

                    //4.设置上当前商品的秒杀时间信息，这个是根据活动来的
                    redisTo.setStartTime(sesssion.getStartTime().getTime());
                    redisTo.setEndTime(sesssion.getEndTime().getTime());

                    String jsonString = JSON.toJSONString(redisTo);
                    //加上秒杀场次的id和商品的id这样的话后面好查询
                    ops.put(seckillSkuVo.getPromotionSessionId().toString()+"_"+seckillSkuVo.getSkuId().toString(),jsonString);

                    //如果当前这个场次的商品的库存信息已经上架就不需要上架
                    /**
                     * .在redis里面设置信号量，这个信号量就算是一个自增量，只要高并发下的流量谁抢到了商品，信号量就减去抢到商品的数量
                     * 所以每一个商品都如果有了这个信号量信息，那么想要秒杀这个商品，系统会先去redis里面获取一个信号量，
                     * 也就是这个商品的100库存减去一个，如果能减了，就把你放行
                     * 再来做后边的处理数据库的商品库存信息扣减操作，如果不能减就不用去后边的数据库进行库存扣减，
                     * 那么这样就会阻塞很短的时间，整个请求就会得到很快的释放，只有每一个请求都很快的释放，能很快的处理完了才可以拥有处理大并发的能力。
                     */
                    //5.每一个商品都要设置分布式的信号量。也即使用库存作为分布式的信号量
                    //信号量的一大作用也是：限流
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                    //商品可以秒杀的件数作为信号量
                    semaphore.trySetPermits(seckillSkuVo.getSeckillCount()); //商品有多少就放多少的信号量，只要真正一个秒杀流量进来就减去一个信号
                    // 设置过期时间。
//                    semaphore.expireAt(sesssion.getEndTime());
                }


            });
        });

    }
}
