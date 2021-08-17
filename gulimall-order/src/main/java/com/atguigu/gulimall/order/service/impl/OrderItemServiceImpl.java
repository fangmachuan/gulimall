package com.atguigu.gulimall.order.service.impl;

import com.atguigu.gulimall.order.entity.OrderReturnReasonEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.service.OrderItemService;

@RabbitListener(queues = {"hello-java-queue"})
@Service("orderItemService")
public class OrderItemServiceImpl extends ServiceImpl<OrderItemDao, OrderItemEntity> implements OrderItemService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderItemEntity> page = this.page(
                new Query<OrderItemEntity>().getPage(params),
                new QueryWrapper<OrderItemEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * queues：声明需要监听的所有队列
     *
     * org.springframework.amqp.core.Message
     *
     * 参数可以写一下类型
     * 1、Message message：原生消息详细信息。头+体
     * 2、T<发送的消息的类型> OrderReturnReasonEntity content；
     * 3、Channel channel：当前传输数据的通道
     *
     * Queue：可以很多人都来监听。只要收到消息，队列删除消息，而且只能有一个收到此消息
     * 场景：
     *    1）、订单服务启动多个；同一个消息，只能有一个客户端收到
     *    2)、 只有一个消息完全处理完，方法运行结束，我们就可以接收到下一个消息
     */
//    @RabbitListener(queues = {"hello-java-queue"})
    @RabbitHandler
    public void recieveMessage(Message message,
                               OrderReturnReasonEntity content,
                               Channel channel) throws InterruptedException {
        //{"id":1,"name":"哈哈","sort":null,"status":null,"createTime":1581144531744}
        System.out.println("接收到消息..."+content);
        byte[] body = message.getBody();
        //消息头属性信息
        MessageProperties properties = message.getMessageProperties();
        System.out.println("消息处理完成=>"+content.getName());
        //channel内按顺序自增的。
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        System.out.println("deliveryTag==>"+deliveryTag);

        //签收货物,非批量模式
        try {//因为告诉服务端，这个消息签收了，肯定相当于要给服务端发送消息，所以可能网络会中断，那么这里肯定会有异常
            if(deliveryTag%2 == 0){ //能整除的话，就签收，否则就不签收
                //收货 第一个参数是id，第二个参数是否非批量签收。现在已经设定为只签收当前这个货物，也即做一个消息给它确认一个
                channel.basicAck(deliveryTag,false);
                System.out.println("签收了货物..."+deliveryTag);
            }else {//说明没有签收货物

                /**
                 * //退货 requeue=false 丢弃  requeue=true 发回服务器，服务器重新入队。
                 *                     是否可以批量拒绝     是否发回服务器，让服务器重新入队。
                 * long deliveryTag, boolean multiple, boolean requeue
                 */
                //签收了货物...6
                channel.basicNack(deliveryTag,false,true);
                //long deliveryTag, boolean requeue
//                channel.basicReject();
                System.out.println("没有签收了货物..."+deliveryTag);
            }

        }catch (Exception e){
            //网络中断，这个签收状态发不出去了
        }

    }
}