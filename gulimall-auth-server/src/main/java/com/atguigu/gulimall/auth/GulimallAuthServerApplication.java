package com.atguigu.gulimall.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * SpringSession的核心原理
 *      1.@EnableRedisHttpSession给容器中导入了哪些组件
 *          给容器中导入了RedisHttpSessionConfiguration这个配置
 *          这个RedisHttpSessionConfiguration还继承了一个SpringHttpSessionConfiguration
 *          这个SpringHttpSessionConfiguration还是一个配置，
 *          这个SpringHttpSessionConfiguration给容器中放了哪些内容？
 *              1.1.首先第一个，它先初始化@PostConstruct，其意思就是这个SpringHttpSessionConfiguration类只要一构造起来
 *                   那么构造器就会初始化标注的@PostConstruct下的方法
 *              1.2.第二个，还放了一个SessionRepositoryFilter，这个是过滤器，这个过滤器里面有session的id解析器等
 *                  这个过滤器的作用，就是每一个请求都得来过滤，也就是之前所学的HTTP的Filter
 *      2. RedisHttpSessionConfiguration这个配置又做了什么事情？
 *          2.1：给容器中添加了RedisIndexedSessionRepository这个组件
 *                2.1.1：就是redis操作session，相当于是给session的增删改查都是使用redis来操作
 *
 */

@EnableRedisHttpSession
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class GulimallAuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallAuthServerApplication.class, args);
    }

}
