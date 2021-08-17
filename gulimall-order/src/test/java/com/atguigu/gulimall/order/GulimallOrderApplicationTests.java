package com.atguigu.gulimall.order;

import com.atguigu.gulimall.order.entity.OrderReturnReasonEntity;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

@Slf4j
@SpringBootTest
class GulimallOrderApplicationTests {

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * 创建交换机
     */
    @Test
    void createExchange() {
        /**
         *                        交换机名字     是否持久化        不自动删除             交换机创建的时候还可以创建一些参数
         * public DirectExchange(String name, boolean durable, boolean autoDelete, Map<String, Object> arguments) {
         *
         */
        DirectExchange directExchange = new DirectExchange("hello-java-exchange",true,false);
        amqpAdmin.declareExchange(directExchange);
        log.info("Exchange[{}]创建成功","hello-java-exchange");
    }

    /**
     * 创建队列
     */
    @Test
    public void createQueue(){
        /**
         *                 队列名字      是否持久化      只有一个连接的话，其他就不能连接   是否自动删除                 创建队列的时候还可以创建一些参数
         *  public Queue(String name, boolean durable, boolean exclusive,           boolean autoDelete, @Nullable Map<String, Object> arguments) {
         */
        Queue queue = new Queue("hello-java-queue",true,false,false);
        amqpAdmin.declareQueue(queue);
        log.info("Queue[{}]创建成功","hello-java-queue");
    }

    /**
     * 队列和交换机进行绑定
     */
    @Test
    public void createBinding(){
        /**
         *                      目的地               目的地类型                              交换机            路由键              自定义参数
         *  public Binding(String destination, Binding.DestinationType destinationType, String exchange, String routingKey, @Nullable Map<String, Object> arguments) {
         *  将exchange指定的交换机和destination目的地进行绑定，然后使用routingKey作为指定的路由键
         */
        Binding binding = new Binding("hello-java-queue", Binding.DestinationType.QUEUE,"hello-java-exchange","hello.java",null);
        amqpAdmin.declareBinding(binding);
        log.info("Binding[{}]创建成功","hello-java-binding");
    }

    /**
     * 测试给rabbitmq进行发消息
     */
    @Test
    public void sendMessageTest(){



        /**
         *  第一个参数：交换机
         *  第二个参数：路由建
         *  第三个参数：发送的消息是什么
         */
        String msg = "Hello World!";
        for (int i = 0; i <10 ; i++) {
            OrderReturnReasonEntity reasonEntity = new OrderReturnReasonEntity();
            reasonEntity.setId(1L);
            reasonEntity.setCreateTime(new Date());
            reasonEntity.setName("哈哈-"+i);
            rabbitTemplate.convertAndSend("hello-java-exchange","hello.java",reasonEntity);
            log.info("消息发送完成{}",reasonEntity);
        }

    }

}
