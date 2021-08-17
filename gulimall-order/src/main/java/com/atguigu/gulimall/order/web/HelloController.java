package com.atguigu.gulimall.order.web;

import com.atguigu.gulimall.order.entity.OrderEntity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.UUID;

@Controller
public class HelloController {


    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     *
     * 测试定时关单
     * @return
     */
    @ResponseBody
    @GetMapping("/test/createOrder")
    public String createOrderTest(){
        //订单下单成功
        OrderEntity entity = new OrderEntity();
        entity.setOrderSn(UUID.randomUUID().toString()); //订单号
        entity.setModifyTime(new Date());

        //给MQ发送一个消息                        要给哪个交换机发消息      第一次只要订单创建成功用的路由建   发的消息的对象
        rabbitTemplate.convertAndSend("order-event-exchange","order.create.order",entity);
        return "ok";
    }


    @GetMapping("/{page}.html")
    public String listPage(@PathVariable("page") String page){


        return page;
    }
}
