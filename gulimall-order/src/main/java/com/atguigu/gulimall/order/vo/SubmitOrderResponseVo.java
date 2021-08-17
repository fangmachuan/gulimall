package com.atguigu.gulimall.order.vo;

import com.atguigu.gulimall.order.entity.OrderEntity;
import lombok.Data;

/**
 * 整个下单操作的返回数据
 */
@Data
public class SubmitOrderResponseVo {

    private OrderEntity order; //下单成功，订单的信息就存在这
    private Integer code;//0成功   如果不是0就是错误
}
