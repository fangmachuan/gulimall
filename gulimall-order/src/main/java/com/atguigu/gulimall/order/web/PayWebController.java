package com.atguigu.gulimall.order.web;

import com.alipay.api.AlipayApiException;
import com.atguigu.gulimall.order.config.AlipayTemplate;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.PayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PayWebController {


    @Autowired
    AlipayTemplate alipayTemplate;

    @Autowired
    OrderService orderService;

    /**
     * 去支付一个订单
     * 支付成功后，要跳到用户的订单列表页
     * orderSn  订单号
     */
    @ResponseBody
    @GetMapping(value = "/payOrder",produces = "text/html") //要产生这种类型的数据
    public String payOrder(@RequestParam("orderSn") String orderSn) throws AlipayApiException {
       PayVo payVo = orderService.getOrderPay(orderSn); //获取订单的支付信息
        String pay = alipayTemplate.pay(payVo);
        System.out.println(pay);
        //返回的是一个支付页面，将此页面直接交给浏览器就行了，就能进入支付页面
        return pay;
    }
}
