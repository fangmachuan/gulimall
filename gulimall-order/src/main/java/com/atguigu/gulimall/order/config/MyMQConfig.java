package com.atguigu.gulimall.order.config;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class MyMQConfig {

       //结合MQ的延时队列，完成库存的自动解锁功能



    /**
     * 这个队列里面如果过了一段时间以后，就会死掉，就会发给死信队列的交换机
     * 一旦RabbitMQ创建好相应的队列以后。只要有队列，即使代码上的属性修改了，也不会覆盖到MQ上，那么就直接去MQ上删除相应的队列就行了
     */
    @Bean
    public Queue orderDelayQueue(){
        /**
         *              队列的名字     队列是不是持久化的  是不是排他的          是不是自动删除的               在队列里面有没有自定义属性
         * public Queue(String name, boolean durable, boolean exclusive, boolean autoDelete, @Nullable Map<String, Object> arguments) {
         *
         * 属性设置：
         * x-dead-letter-exchange: order-event-exchange
         * x-dead-letter-routing-key: order.release.order
         * x-message-ttl: 60000
         */
        Map<String,Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange","order-event-exchange"); //死信路由
        arguments.put("x-dead-letter-routing-key","order.release.order"); //死信路由键
        arguments.put("x-message-ttl",120000); //消息过期时间，毫秒为单位 两分钟后
        Queue queue = new Queue("order.delay.queue", true, false, false,arguments);
        return queue;
    }

    //order.release.order.
    @Bean
    public Queue orderReleaseOrderQueue(){
        Queue queue = new Queue("order.release.order.queue", true, false, false);
        return queue;
    }

    //交换机
    @Bean
    public Exchange orderEventExchange(){
        /**
         *                         交换机的名字  是否持久化         是否自动删除          是否有其他参数
         *   public TopicExchange(String name, boolean durable, boolean autoDelete, Map<String, Object> arguments) {
         */
       return new TopicExchange("order-event-exchange",true,false);
    }

    //交换机跟以上两个队列的绑定关系
    @Bean
    public Binding orderCreateOrderBinding (){
        /**
         *                  目的地               目的地的类型                     哪个交换机跟目的地绑定  路由键            是否有其他参数属性
         *  public Binding(String destination, DestinationType destinationType, String exchange, String routingKey, @Nullable Map<String, Object> arguments) {
         */
       return new Binding("order.delay.queue", Binding.DestinationType.QUEUE,"order-event-exchange","order.create.order",null);
    }

    @Bean
    public Binding orderReleaseOrderBinding (){
        return new Binding("order.release.order.queue", Binding.DestinationType.QUEUE,"order-event-exchange","order.release.order",null);
    }


    /**
     * 订单释放直接和库存释放进行绑定
     * @return
     */
    @Bean
    public Binding orderReleaseOtherBinding (){
        return new Binding("stock.release.stock.queue", Binding.DestinationType.QUEUE,"order-event-exchange","order.release.other.#",null);
    }


    /**
     * 订单的秒杀单的监听队列
     * 结合了消息队列MQ，此时在这一块倒不是来做最终一致性，虽然也是要保证最终一致，由订单服务要真正的去创建订单，在数据库粒保存
     * 但是此次更多做的是流量的削峰
     * 请求一进来，不用立即去调用订单服务，直接放到MQ消费队列里面，然后让订单服务慢慢去消费就不至于把订单服务打垮
     */
    @Bean
    public Queue orderSeckillOrderQueue(){
        return new Queue("order.seckill.order.queue",true,false,false);
    }

    @Bean
    public Binding orderSeckillOrderQueueBinding(){
        return new Binding("order.seckill.order.queue", Binding.DestinationType.QUEUE, "order-event-exchange", "order.seckill.order", null);
    }

}
