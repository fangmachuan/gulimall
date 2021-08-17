package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.order.constant.OrderConstant;
import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.entity.PaymentInfoEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnum;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WmsFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.service.PaymentInfoService;
import com.atguigu.gulimall.order.to.OrderCreateTo;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.xml.crypto.Data;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    //本地线程共享
    private ThreadLocal<OrderSubmitVo> confirmVoThreadLocal =new ThreadLocal<>();

    @Autowired
    MemberFeignService memberFeignService;

    @Autowired
    OrderItemService orderItemService;

    @Autowired
    CartFeignService cartFeignService;

    @Autowired
    WmsFeignService wmsFeignService;

    @Autowired
    ThreadPoolExecutor executor;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    PaymentInfoService paymentInfoService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 给订单确认页返回需要用的数据
     * 异步编排的时候，Feign还会丢失上下文的问题
     * @return
     */
    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();//获取到当前登录后的用户
        //在异步任务执行之前，让它放一下数据，先从主线程拿到原来的数据
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        CompletableFuture<Void> getAddressFuture = CompletableFuture.runAsync(() -> {
            //从主线程拿到原来的数据，在父线程里面都共享出来RequestContextHoldereption: Loa
            //只有共享来，拦截器里面才会有数据
            //也即获取之前的请求，每一个线程都来共享之前的请求数据
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //1.先根据当前会员的id查出之前保存的所有收获地址列表信息
            //远程查询会员服务的收获地址信息
            List<MemberAddressVo> address = memberFeignService.getAddress(memberRespVo.getId());
            confirmVo.setAddress(address);
        }, executor);
        CompletableFuture<Void> cartFuture = CompletableFuture.runAsync(() -> {
            //也即获取之前的请求，每一个线程都来共享之前的请求数据
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //2.远程查询购物车所有选中的购物项，也就是用来准备结账的
            List<OrderItemVo> items = cartFeignService.getCurrentUserCartItems();//获取当前用户的购物项数据
            confirmVo.setItems(items);
            //查询到购物车商品以后，再来查询这些商品的所有库存信息
        }, executor).thenRunAsync(()->{
            List<OrderItemVo> items = confirmVo.getItems();
            //批量查询每一个商品的库存信息
            //先来收集好商品id
            List<Long> collect = items.stream().map(item -> item.getSkuId()).collect(Collectors.toList());
            //远程查询库存系统来查相应商品的库存信息
            R hasStock = wmsFeignService.getSkusHasStock(collect);
            //得到每一个商品的库存状态信息
            List<SkuStockVo> data = hasStock.getData(new TypeReference<List<SkuStockVo>>() {
            });
            if (data!=null){
                Map<Long, Boolean> map = data.stream().collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                confirmVo.setStocks(map);
            }
        },executor);
            //3.查询一些用户积分信息
            Integer integration = memberRespVo.getIntegration();
            confirmVo.setIntegration(integration);

        //4.其他价格数据在实体类中自动计算

        //TODO 5.防重令牌
        /**
         *  在订单确认页到来之前，可以为这个页面生成一个令牌，提交订单的时候顺便带上这个令牌
         *      然后验令牌保证幂等性
         *      这个防重令牌保证到redis里面
         *       下一次验证就去redis里面拿即可
         *       然后给页面也放一个令牌
         *       令牌放在两个地方，服务器一个，浏览器一个
         */
        String token = UUID.randomUUID().toString().replace("-", "");
        //参数一： 用户的id，参数二令牌,参数三：30分钟的过期时间
        redisTemplate.opsForValue().set(OrderConstant.USER_ORDER_TOKEN_PREFIX+memberRespVo.getId(),token,30, TimeUnit.MINUTES);//给服务器一个防重令牌
        //给页面一个防重令牌
        confirmVo.setOrderToken(token);
        CompletableFuture.allOf(getAddressFuture,cartFuture).get();//
        return confirmVo;
    }

    /**
     *
     * 本地事务，在分布式系统，只能控制住自己的回滚，控制不了其他服务的回滚，所以要用到分布式事务
     * 分布式事务，使用分布式事务的主要原因就是网络问题，由于这个网络抖动，可能会无法感知远程的这个分布式事务它是真失败还是假失败
     * 包括失败了以后，由于机器拆分（分布式机器）都不是操作一个数据库。所以没办法控制其他服务整个事务的回滚
     * 举个例子：库存成功了，但是可能因为网络超时原因，订单回滚了，库存却不滚。
     *
     * 但是使用seata来控制分布式事务的话会在AT模型下，它有几大步都是获取全局锁和隔离锁的机制的，所以这样一做一个事务，它都会加入超多的锁
     * 一加锁以后相当于把并发变成于一个串行化了，所以如果假设订单等其他模块都这么来做，都得先等上一个订单下完，再来下下一个订单
     * 这样并发就没有起到任何的作用。
     *
     *
     * 所以在这个高并发里面，肯定解决分布式事务不去考虑2PC模式和TCC事务补偿模式
     *
     * 那么在最终的高并发里面，使用
     * 柔性事务：1、可靠消息+最终一致性方案（异步确保型）
     *         2、
     *         这两种方案都是基于消息服务的，也就是可以来通知这个服务这个事干完了，接下来其他服务收到通知以后再继续来干
     *         所以本系统的库存这一块，想要回滚的话，就不使用seata的分布式事务了
     *         最终来使用“可靠消息+最终一致性方案（异步确保型）”
     *
     *     为了保证系统的高并发，假设下订单这一块有错误，回滚了，在库存那一块也得自己回滚
     *     这个库存怎么回滚呢？
     *     所以这个库存服务是可以在订单这一块，比如订单业务做完了以后，知道订单业务出现了异常给回滚了
     *     那么就可以发一个消息告诉库存服务，订单报错回滚了，让库存服务锁定的库存进行一个解锁
     *     所以不需要调用回滚的代码，只需要发一个消息，就会让整个系统的性能损失是几乎没有的
     *
     *
     *     但是库存服务本身也是可以自动解锁的模式，这样的话，订单服务下订单的时候，连消息都不用发了。无论是成了败了，库存服务都会自动解锁
     *     要怎么自动解锁呢？
     *     要参与消息队列来完成这个事情
     *     如果哪些库存想要解锁，就需要订单发一个消息给库存服务，然后又一个专门的库存解锁服务去来订阅这个消息
     *     系统可以不用保证强一致性，能保证最终一致性即可
     *     所以可以使用消息队列来完成这个最终的一致性
     *
     *  @Transactional(isolation = Isolation.READ_COMMITTED): 就是在这个事务期间内都能读到已提交的数据】
     */
    //@GlobalTransactional  //seata的全局事务
    //结算确认页提交订单，下单功能业务
    @Transactional
    @Override
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo vo) {
        //创建订单方法之前，将页面传来的数据都可以放到了ThreadLocal本地线程变量里面
        confirmVoThreadLocal.set(vo);
        SubmitOrderResponseVo response = new SubmitOrderResponseVo();
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();//获取到当前登录后的用户
        response.setCode(0);
        //1.验证防重令牌，看看令牌是否合法,浏览器传过来的令牌跟redis当中的令牌进行对比【并且令牌的对比和删除必须保证是原子性的】
        //脚本的意思：如果redis调用get方法来获取一个key的值等于传过来的值，就会返回令牌删除，否则返回0
        //最终脚本：返回的是0和1，如果获取指定的值，这个值如果不存在就删不了就返回0，如果存在它就会调用删除，如果删除成功了就返回1，否则返回0
        //0：令牌校验失败
        //1：删除成功，也即令牌对比成功了才删除成功
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"; //如果使用token机制，就应该使用脚本的方式来对比和删令牌
        String orderToken = vo.getOrderToken();//拿到页面传过来的值
        //第一个参数：脚本信息和返回的脚本类型，第二个参数：获取到哪个的值，也即要验证的key 第三个参数：就是要对比的值   把页面的值和服务器端的值来做一个对比操作
        //原子验证防重令牌和删锁令牌操作的脚本
        Long result = redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberRespVo.getId()), orderToken);//执行脚本
        if (result == 0L){
            //防重令牌验证失败，就返回给控制器
            response.setCode(1);
            return response;
        }else {
            //防重令牌验证成功，也说明删除令牌成功
            //令牌验证通过就进行创建下单操作：去服务器创建订单,验令牌，验价格，锁库存等操作....
            OrderCreateTo order = createOrder();//创建订单
            //验价
            BigDecimal payAmount = order.getOrder().getPayAmount();
            BigDecimal payPrice = vo.getPayPrice();
            if(Math.abs(payAmount.subtract(payPrice).doubleValue())<0.01){
                //金额对比
                //3。保存订单到数据库
                saveOrder(order);
                //4.库存锁定，只要订单一保存就要锁库存，只要有异常，就回滚订单数据
                //订单号：给哪个订单锁的库存
                //所有订单项（商品的skuid，锁了几件商品，商品名字）
                WareSkuLockVo lockVo = new WareSkuLockVo();
                lockVo.setOrderSn(order.getOrder().getOrderSn()); //为这个订单号锁库存
                List<OrderItemVo> locks = order.getOrderItems().stream().map(item -> {
                    OrderItemVo itemVo = new OrderItemVo();
                    itemVo.setSkuId(item.getSkuId());
                    itemVo.setCount(item.getSkuQuantity());//订单项的数量
                    itemVo.setTitle(item.getSkuName());
                    return itemVo;
                }).collect(Collectors.toList());
                lockVo.setLocks(locks); //锁定的所有订单项数据
                //只要订单保存成功就远程调用库存服务，然后锁库存，防止订单支付成功以后还没有库存
                //TODO 远程锁库存
                R r = wmsFeignService.orderLockStock(lockVo);
                if (r.getCode() == 0){//锁成功了
                    response.setOrder(order.getOrder());
                    //TODO 订单创建成功就发送消息给MQ

                    /**
                     * 只要订单一创建成功，就会使用路由键（order.create.order",order）给交换机（order-event-exchange）发送消息
                     * 消息就先会按照路由键（）来到延时队列
                     * 延时一定时间以后，只要过期了，就会以另外一种路由键的方式，这都是自动配置的，然后来到死信队列，
                     * 最后监听死信队列的消费者，就会收到到死信队列里面要关闭的订单数据
                     *
                     * 第一个参数：给哪个交换机发消息
                     * 第二个参数：给这个交换机的谁发消息？（也即路由键）
                     * 第三个参数：发的消息是什么？ 把创建成功的订单数据放到这里
                     */
                    rabbitTemplate.convertAndSend("order-event-exchange","order.create.order",order.getOrder());
                    return response;
                }else {  //锁定失败了，抛异常回滚事务
                    String msg = (String) r.get("msg");
                   throw new  NoStockException(msg);
                }
            }else {
                response.setCode(2); //金额对比失败
                return response;
            }
        }
    }

    /**
     * 按照订单号获取订单
     * @param orderSn
     * @return
     */
    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        OrderEntity order_sn = this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
        return order_sn;
    }

    /**
     * 关单方法
     * 想要关单，其实就是把订单表里面的状态一修改即可
     * 但是该之前要来查询一下，因为万一之前已经支付成功了，来到消息队列里面的这个消息是每一个订单都过来的，但是有些订单已经成功了
     * 那么成功了就要在关单之前一定要查这个订单当前状态是什么
     * @param entity  关哪个订单
     */
    @Override
    public void closeOrder(OrderEntity entity) {
        //关闭订单之前，先来查询这个订单的最新状态
        //因为之前订单创建成功，直接发的是数据库保存的这个对象，所以数据库保存的这个对象是直接有自增id的，所以直接拿到这个订单的id
        OrderEntity orderEntity = this.getById(entity.getId());
        //判断一下当前的状态
        /**
         * 什么情况下才需要关单
         * 只有待付款当中才能关单
         */
        if (orderEntity.getStatus() == OrderStatusEnum.CREATE_NEW.getCode()){
            //就进行关单（改一个状态）
            OrderEntity update = new OrderEntity();
            update.setId(entity.getId());
            update.setStatus(OrderStatusEnum.CANCLED.getCode());
            this.updateById(update);
            OrderTo orderTo = new OrderTo();
            BeanUtils.copyProperties(orderEntity, orderTo);
            //只要订单释放了，再给订单服务的交换机发一个消息，然后让订单服务的这个交换机与库存绑定的库存释放的队列进行发送消息，告知订单释放成功
            try {
                //TODO 保证消息一定会发送出去 可以每发一个消息，都做好相应的日志记录
                rabbitTemplate.convertAndSend("order-event-exchange","order.release.other",orderTo);
            }catch (Exception e){
                //TODO 将没发送成功的消息进行重试发送

            }

        }
    }

    /**
     * 获取当前订单的支付信息
     * @param orderSn  订单号
     * @return
     */
    @Override
    public PayVo getOrderPay(String orderSn) {
        PayVo payVo = new PayVo();
        //查询订单数据
        OrderEntity order = this.getOrderByOrderSn(orderSn);
        BigDecimal decimal = order.getPayAmount().setScale(2, BigDecimal.ROUND_UP);//如果有小数就向上取值
        payVo.setOut_trade_no(order.getOrderSn());
        payVo.setTotal_amount(decimal.toString());
        List<OrderItemEntity> order_sn = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        OrderItemEntity entity = order_sn.get(0);//拿到商品的第一个名字


        payVo.setBody(entity.getSkuAttrsVals());
        payVo.setSubject(entity.getSkuName());
        return payVo;
    }

    /**
     *查询当前用户的所有订单列表页的详情数据
     * @param params
     * @return
     */
    @Override
    public PageUtils queryPageWithItem(Map<String, Object> params) {
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();//获取到当前用户

        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                //按照订单的id来进行降序排序
                new QueryWrapper<OrderEntity>().eq("member_id",memberRespVo.getId()).orderByDesc("id")//查询当前用户的
        );
        //把每一个订单拿来再查一下订单的订单项
        List<OrderEntity> order_sn = page.getRecords().stream().map(order -> {
            //按照订单号来查询订单项详情信息
            List<OrderItemEntity> itemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", order.getOrderSn()));
            order.setItemEntities(itemEntities);
            return order;
        }).collect(Collectors.toList());

        page.setRecords(order_sn);

        return new PageUtils(page);
    }

    /**
     * 处理支付宝的支付结果
     * @param vo
     * @return
     */
    @Override
    public String handlePayResult(PayAsyncVo vo) {
        //1.保存交易的流水信息
        PaymentInfoEntity infoEntity = new PaymentInfoEntity();
        infoEntity.setAlipayTradeNo(vo.getTrade_no());
        infoEntity.setOrderSn(vo.getOut_trade_no());
        infoEntity.setPaymentStatus(vo.getTrade_status());
        infoEntity.setCallbackTime(vo.getNotify_time());
        paymentInfoService.save(infoEntity);

        //2.修改订单的状态信息
        if (vo.getTrade_status().equals("TRADE_SUCCESS") || vo.getTrade_status().equals("TRADE_FINISHED")) {
            //支付成功的状态
            String outTradeNo = vo.getOut_trade_no();
            this.baseMapper.updateOrderStatus(outTradeNo,OrderStatusEnum.PAYED.getCode());
        }
        return "success";
    }

    /**
     * 订单处理MQ的秒杀单信息
     * 只要一秒杀成功，队列里面一收到消息，订单服务就会为我们来创建一个订单
     * @param seckillOrder
     */
    @Override
    public void createSeckillOrder(SeckillOrderTo seckillOrder) {
        //TODO 保存订单的信息
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(seckillOrder.getOrderSn());
        orderEntity.setMemberId(seckillOrder.getMemberId());
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode()); //待付款
        BigDecimal multiply = seckillOrder.getSeckillPrice().multiply(new BigDecimal("" + seckillOrder.getNum()));
        orderEntity.setPayAmount(multiply); //支付的所有信息，要付多少钱
        this.save(orderEntity);
        //保存订单项的信息
        OrderItemEntity orderItemEntity = new OrderItemEntity();
        orderItemEntity.setOrderSn(seckillOrder.getOrderSn());
        orderItemEntity.setRealAmount(multiply); //真实要付的价格
        orderItemEntity.setSkuQuantity(seckillOrder.getNum());//买了多少件
        orderItemService.save(orderItemEntity);

    }

    /**
     * 保存订单数据
     * @param order
     */
    private void saveOrder(OrderCreateTo order) {
        OrderEntity orderEntity = order.getOrder();
        orderEntity.setModifyTime(new Date());
        this.save(orderEntity);
        //保存所有的订单项
        List<OrderItemEntity> orderItems = order.getOrderItems();
        orderItemService.saveBatch(orderItems);

    }

    /**
     * 创建订单数据
     * 下订单流程
     * @return
     */
     private OrderCreateTo createOrder(){
         OrderCreateTo createTo = new OrderCreateTo();
         //1.生成订单号
         String orderSn = IdWorker.getTimeId();//使用mybatisplus提供的订单id不重复的，当做订单号
         //按照指定的订单号构建一个订单
         OrderEntity orderEntity = buildOrder(orderSn);
         //2.订单构建好了以后，构建每一个订单项，不过得先知道为哪个订单构建的订单项
         List<OrderItemEntity> itemEntities = buildOrderItems(orderSn);
         //3.验价格 计算出订单里面的信息，最终验价
         //参数一：哪个订单，参数二：订单项数据，最终计算来的价格会放在订单的实体对象里面
        computePrice(orderEntity,itemEntities);
        createTo.setOrder(orderEntity);
        createTo.setOrderItems(itemEntities);
         return createTo;
     }

    /**
     * 计算价格 验价格
     * @param orderEntity
     * @param itemEntities
     */
    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> itemEntities) {
        BigDecimal total = new BigDecimal("0.0");//默认的总价格
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal integration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");

        BigDecimal gift = new BigDecimal("0.0");
        BigDecimal growth = new BigDecimal("0.0");
        //订单的总额,叠加每一个订单项的总额信息，也即所有购物项相加的总额
        for (OrderItemEntity entity : itemEntities) {
            coupon = coupon.add(entity.getCouponAmount());
            integration = integration.add(entity.getIntegrationAmount());
            promotion = promotion.add(entity.getPromotionAmount());
            total = total.add(entity.getRealAmount());
            //当前订单能获得的积分信息
            gift= gift.add(new BigDecimal(entity.getGiftIntegration().toString()));
            growth = growth.add(new BigDecimal(entity.getGiftGrowth().toString()));
            //计算每一项的价格：单价乘以数量
           // BigDecimal decimal = entity.getSkuPrice().multiply(new BigDecimal(entity.getSkuQuantity().toString()));
        }
        //1.订单价格相关
        orderEntity.setTotalAmount(total);
        //设置应付总额
        orderEntity.setPayAmount(total.add(orderEntity.getFreightAmount()));
        orderEntity.setPromotionAmount(promotion);
        orderEntity.setIntegrationAmount(integration);
        orderEntity.setCouponAmount(coupon);
        //设置积分等信息
        orderEntity.setIntegration(gift.intValue());
        orderEntity.setGrowth(growth.intValue());

        orderEntity.setDeleteStatus(0); //0代表未删除状态
    }

    private OrderEntity buildOrder(String orderSn) {
        MemberRespVo respVo = LoginUserInterceptor.loginUser.get();//获取登录后的用户
        //整个订单的生成就应该是这个OrderEntity实体类的信息，这个实体类信息里面保存好订单号
        OrderEntity entity = new OrderEntity();
        entity.setOrderSn(orderSn); //保存订单号
        entity.setMemberId(respVo.getId());
        OrderSubmitVo submitVo = confirmVoThreadLocal.get();//获取到页面传递来的数据
        //2.远程查询收货人的地址信息和计算运费信息
        R fare = wmsFeignService.getFare(submitVo.getAddrId());//获取收货地址的邮费信息
        //解析运费信息
        FareVo fareResp = fare.getData(new TypeReference<FareVo>() {
        });
        //给订单里面填充运费信息
        entity.setFreightAmount(fareResp.getFare());
        //给订单里面填充收货人信息
        entity.setReceiverCity(fareResp.getAddress().getCity());
        entity.setReceiverDetailAddress(fareResp.getAddress().getDetailAddress());
        entity.setReceiverName(fareResp.getAddress().getName());
        entity.setReceiverPhone(fareResp.getAddress().getPhone());
        entity.setReceiverPostCode(fareResp.getAddress().getPostCode());
        entity.setReceiverProvince(fareResp.getAddress().getProvince());
        entity.setReceiverRegion(fareResp.getAddress().getRegion());

        //设置订单的相关状态信息
        entity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        entity.setAutoConfirmDay(7);
        return entity;
    }

    /**
     * 构建所有订单项数据
     *
     * @return
     */
    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        //3.填充订单里面的订单项信息
        //最后确定每个购物项的价格，即使后台调价，最终还是会依照现在确定的价格来进行购买商品的
        List<OrderItemVo> currentUserCartItems = cartFeignService.getCurrentUserCartItems();//远程获取到当前用户的购物车当中购物项
        if (currentUserCartItems!=null && currentUserCartItems.size()>0){
            //每一个购物车里面的购物项数据都要映射成最终的订单信息
            List<OrderItemEntity> itemEntities = currentUserCartItems.stream().map(cartItem -> {
                // 构建出每个的订单项,也就是说每一个订单信息，都要构建成一个订单项，最终返回订单项信息
                OrderItemEntity itemEntity =  buildOrderItem(cartItem);
                itemEntity.setOrderSn(orderSn);//保存订单号
                return itemEntity;
            }).collect(Collectors.toList());
            return itemEntities;
        }
        return null;
    }

    /**
     * 构建指定的某一个订单项
     * @param cartItem
     * @return
     */
    private OrderItemEntity buildOrderItem(OrderItemVo cartItem) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        //1、订单信息：订单号

        //2.商品的spu信息
        Long skuId = cartItem.getSkuId();
        R r = productFeignService.getSpuInfoBySkuId(skuId);
        SpuInfoVo data = r.getData(new TypeReference<SpuInfoVo>() {
        });
        itemEntity.setSpuId(data.getId());
        itemEntity.setSpuName(data.getSpuName());
        itemEntity.setSpuBrand(data.getBrandId().toString());
        itemEntity.setCategoryId(data.getCatalogId());

        //3.商品的sku信息
        itemEntity.setSkuId(cartItem.getSkuId());
        itemEntity.setSkuName(cartItem.getTitle());
        itemEntity.setSkuPic(cartItem.getImage());
        itemEntity.setSkuPrice(cartItem.getPrice());
        String skuAttr = StringUtils.collectionToDelimitedString(cartItem.getSkuAttr(), ";");
        itemEntity.setSkuAttrsVals(skuAttr);
        itemEntity.setSkuQuantity(cartItem.getCount());
        //4.优惠信息【忽略不做】

        //5.积分信息
        itemEntity.setGiftGrowth(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount().toString())).intValue());
        itemEntity.setGiftIntegration(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount().toString())).intValue());

        //6.订单项的价格相关信息
        //将每一个订单项的总价格计算出来
        itemEntity.setPromotionAmount(new BigDecimal("0"));
        itemEntity.setCouponAmount(new BigDecimal("0"));
        itemEntity.setIntegrationAmount(new BigDecimal("0"));
        //当前订单项的实际金额计算，总价乘以数量
        BigDecimal orign = itemEntity.getSkuPrice().multiply(new BigDecimal(itemEntity.getSkuQuantity().toString()));
        //总额减去各种优惠
        BigDecimal subtract = orign.subtract(itemEntity.getCouponAmount()).subtract(itemEntity.getPromotionAmount()).subtract(itemEntity.getIntegrationAmount());
        itemEntity.setRealAmount(subtract);
        return itemEntity;
    }

}