package com.atguigu.gulimall.seckill.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
//@EnableAsync //开启异步任务
//@EnableScheduling
public class HelloSchedule {


//    /**
//     * 1测试第一个：秒分时日月周
//     */
//    @Scheduled(cron = "* * * * * ?")
//    public void hello(){
//        log.info("hello....");
//    }
}
