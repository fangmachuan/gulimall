package com.atguigu.gulimall.ware.vo;

import lombok.Data;

@Data
public class LockStockResult {

    private Long skuId;//商品的id
    private Integer num; //锁了几件商品
    private Boolean locked; //是否锁定成功
}
