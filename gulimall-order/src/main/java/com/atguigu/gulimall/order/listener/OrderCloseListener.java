package com.atguigu.gulimall.order.listener;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * @author: hhf
 * @create: 2020-05-22 22:55
 * 订单30分钟后自动关单的监听器
 **/
@RabbitListener(queues = "order.release.order.queue") //监听订单的释放队列，能到这个队列里面的消息，都是30分钟后过来的
@Service
public class OrderCloseListener {

    @Autowired
    OrderService orderService;


    /**
     *
     * 生产者发了一个订单，订单创建成功了，消息发出去以后，两分钟以后能不能监听到这个消息
     * 消费者监听order.release.order.queue这个队列，收到消息才是两分钟以后过期的
     */
    @RabbitHandler
    public void listener(OrderEntity entity, Channel channel, Message message) throws IOException {
        System.out.println("收到过期的订单信息：准备关闭订单"+entity.getOrderSn());
        try {  //尝试关单，如果一切正常。就回复ACK机制
            orderService.closeOrder(entity);//自动关闭订单
            //可以手动调用支付宝进行收单

            /**
             *  参数1： 要拿到原生消息
             *  参数2： 是不是要批量告诉MQ，已经成功了，现在只设置了告诉一个
             */
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);    //通过通道告诉MQ，已经收到消息了
        }catch (Exception e){  //如果出现异常，消息就拒绝，让它重新回到消息队列当中
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }


    }

}
