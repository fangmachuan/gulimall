package com.atguigu.gulimall.ware.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;


/**
 * 给容器中放一个消息转换器
 */
@Configuration
public class MyRabbitConfig {

//    @Autowired
    RabbitTemplate rabbitTemplate;


    @Primary
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory){
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        this.rabbitTemplate = rabbitTemplate;
        rabbitTemplate.setMessageConverter(messageConverter());
//        initRabbitTemplate();

        return rabbitTemplate;
    }

    /**
     * 使用JSON序列化机制，进行消息的转换
     * @return
     */
    @Bean
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }

//    //第一次连上MQ监听消息的时候，就会创建相应的队列
//    @RabbitListener(queues = "stock.release.stock.queue")
//    public void  handle(Message message){
//
//    }


    /**
     * 库存服务的交换机
     * @return
     */
    @Bean
    public Exchange stockEventExchange(){
      return   new TopicExchange("stock-event-exchange",true,false);
    }

    @Bean
    public Queue stockReleaseStockQueue(){
        return new Queue("stock.release.stock.queue",true,false,false);
    }

    //延迟队列
    @Bean
    public Queue stockDelayQueue(){
        /**
         *
         * x-dead-letter-exchange: stock-event-exchange 信死了，是哪个路由
         * x-dead-letter-routing-key: order.release.order 路由键
         * x-message-ttl: 120000    消息的过期时间
         *
         */
        Map<String,Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange","stock-event-exchange");
        args.put("x-dead-letter-routing-key","stock.release");
        args.put("x-message-ttl",180000); //3分钟
        return new Queue("stock.delay.queue",true,false,false,args);
    }

    //交换机和队列的绑定关系
    @Bean
    public Binding stockReleaseBinding(){
        return  new Binding("stock.release.stock.queue",
                Binding.DestinationType.QUEUE,
                "stock-event-exchange",
                "stock.release.#",
                null);
    }

    @Bean
    public Binding stockLockedBinding(){
        return  new Binding("stock.delay.queue",
                Binding.DestinationType.QUEUE,
                "stock-event-exchange",
                "stock.locked",
                null);
    }

}
