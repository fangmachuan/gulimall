package com.atguigu.gulimall.order.to;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateTo {


    private OrderEntity order; //创建好的订单信息

    private List<OrderItemEntity> orderItems; //订单项

    private BigDecimal payPrice;//订单计算的应付价格 ，当然这个应付价格也按照运费进行计算的

    private BigDecimal fare;//运费
}
