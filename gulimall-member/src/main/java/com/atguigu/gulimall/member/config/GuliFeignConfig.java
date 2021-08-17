package com.atguigu.gulimall.member.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Configuration
public class GuliFeignConfig {

    /**
     * 解决Feign远程调用丢失请求体问题
     * @return
     */

    @Bean("requestInterceptor")
    public RequestInterceptor requestInterceptor(){
       return new RequestInterceptor(){

           /**
            *
            * @param template feign里面的新请求
            */
           @Override
           public void apply(RequestTemplate template) {
               //RequestContextHolder可以拿到刚进来的这个请求数据，也即调用我们controller下的请求方法当时的一些请求头信息
               ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
               if (attributes!=null){
                   HttpServletRequest request = attributes.getRequest();//获取到当前请求的对象，也即老请求
               if (request != null){
                   //把获取到的请求对象的头信息都同步进来,主要同步cookie，
                   String cookie = request.getHeader("Cookie");
                   ////给新请求同步了老请求的头信息，这样最终远程调用的时候，
                   template.header("Cookie",cookie);
                   System.out.println("feign远程之前先进行RequestInterceptor.apply()");
               }
               }
           }
       };
    }
}
