package com.atguigu.gulimall.seckill.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("gulimall-coupon")
public interface ConponFeignService {


    /**
     * 查询数据库最近三天的秒杀商品活动
     * @return
     */
    @GetMapping("/coupon/seckillsession/laess3DaySession")
    public R getLaess3DaySession();
}
