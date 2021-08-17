package com.atguigu.gulimall.ware.listener;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.StockDetailTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.atguigu.gulimall.ware.vo.OrderVo;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * @author: hhf
 * @create: 2020-05-22 14:41
 *
 * 库存释放的监听器
 **/

@Service
@RabbitListener(queues = "stock.release.stock.queue")
public class StockReleaseListener {

    @Autowired
    WareSkuService wareSkuService;

    //这是库存过期的处理
    @RabbitHandler
    public void handleStockLockedRelease(StockLockedTo to, Message message, Channel channel) throws IOException {
        //所有消息进来了，都会挨个消息进来解锁
        System.out.println("收到解锁库存的消息");
        try {
            //只要有异常，那就说明消息消费失败
            wareSkuService.unlockStock(to);//解锁库存方法
            //如果上面的方法执行成功了就回复成功
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }catch (Exception e){
            //只要收到异常，就拒绝，然后把消息直接放会队列里面，让重新消费
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }
    }

    /**
     * 这是订单已被关闭的处理
     * 接收发出去的所有订单数据
     * 也即订单关了，也要主动解一个库存
     */
    @RabbitHandler
    public void handleOrderCloseRelease(OrderTo orderTo, Message message, Channel channel) throws IOException {

        System.out.println("订单关闭准备解锁库存...");
        //写成功了返回解锁成功
        try {
            wareSkuService.unlockStock(orderTo);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }catch (Exception e){//写失败了就不返回
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }

    }
}
