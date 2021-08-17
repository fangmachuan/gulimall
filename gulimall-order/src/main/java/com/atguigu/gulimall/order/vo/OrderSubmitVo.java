package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 准备提交订单的数据
 */
@Data
public class OrderSubmitVo {
    private Long addrId;//收获地址的id
    private Integer payType; //支付方式
    //无需再提交需要购买的商品，只需去购物车再获取一遍商品即可

    private String note;//订单备注

    private String orderToken;//防重令牌

    private BigDecimal payPrice;//应付价格 可以拿去验价

}
