package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderItemVo {

    private Long skuId;
    private String title;
    private String image;
    private List<String> skuAttr;
    private BigDecimal price;
    private Integer count;
    private BigDecimal totalPrice;
    //是否有货
//    //TODO 查询库存状态
//    private boolean hasStock;

    //返回每一个商品的重量
    private BigDecimal weight;


}
