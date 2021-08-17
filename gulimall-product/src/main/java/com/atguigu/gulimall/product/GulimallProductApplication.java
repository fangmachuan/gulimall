package com.atguigu.gulimall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * @Cacheable: Triggers cache population.: 触发将数据保存到缓存的操作
 *
 * @CacheEvict: Triggers cache eviction.: 触发将数据从缓存删除的操作
 *
 * @CachePut: Updates the cache without interfering with the method execution.: 不影响方法执行更新缓存
 *
 * @Caching: Regroups multiple cache operations to be applied on a method.: 组合以上多个操作
 *
 * @CacheConfig: Shares some common cache-related settings at class-level.: 在类级别共享缓存的相同配置
 *
 * Spring Cache缓存使用步骤：
 *   0、在配置文件中加入：spring.cache.type=redis
 *   1、开启缓存的功能 @EnableCaching
 *   2、只需要使用注解就能完成相关缓存的操作
 */
@EnableRedisHttpSession
@EnableFeignClients(basePackages = "com.atguigu.gulimall.product.feign")
@EnableDiscoveryClient
@MapperScan("com.atguigu.gulimall.product.dao")
@SpringBootApplication
public class GulimallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallProductApplication.class, args);
    }

}
