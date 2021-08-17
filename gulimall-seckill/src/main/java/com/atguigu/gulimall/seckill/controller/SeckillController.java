package com.atguigu.gulimall.seckill.controller;

import com.atguigu.common.utils.R;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SecKillSkuRedisTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
public class SeckillController {

    @Autowired
    SeckillService seckillService;
    /**
     * 返回当前时间可以参与的秒杀商品信息
     */
    @ResponseBody
    @GetMapping("/currentSeckillSkus")
    public R getCurrentSeckillSkus(){
        log.info("currentSeckillSkus正在执行....");
      List<SecKillSkuRedisTo> vos = seckillService.getCurrentSeckillSkus();
        return R.ok().setData(vos);
    }

    /**
     * 获取当前的秒杀信息，（也即详情页那一块的商品秒杀预告）
     * @return
     */
    @ResponseBody
    @GetMapping("/sku/seckill/{skuId}")
    public R getSkuSeckillInfo(@PathVariable("skuId") Long skuId){
        SecKillSkuRedisTo to =  seckillService.getSkuSeckillInfo(skuId);
        return R.ok().setData(to);
    }

    /**
     * 想要进入这一块的逻辑，必须先登录
     *  秒杀请求，商品详情页点击立即抢购以后跳转的那一块
     *
     *  秒杀服务一定要更快的执行更快的释放，也尽量不要让秒杀服务大量的远程调用其他服务，
     *  把别人调用炸，所以秒杀这一块用MQ的消息队列来做一个削峰处理
     * @param killId 秒杀用的id
     * @param key  随机码
     * @param num  秒杀几件
     * @return
     */
    @GetMapping("/kill")
    public String seckill(@RequestParam("killId") String killId, @RequestParam("key") String key, @RequestParam("num") Integer num, Model model){
            //1.判断是否登录（已完成）
        //秒杀成功就返回订单号，否则就没有这个订单号
       String orderSn = seckillService.kill(killId,key,num);
        model.addAttribute("orderSn",orderSn);
        return "success";
    }
}
