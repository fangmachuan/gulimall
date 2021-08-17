package com.atguigu.gulimall.ware.vo;

import com.atguigu.gulimall.ware.vo.OrderItemVo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

//订单确认页需要用的数据
public class OrderConfirmVo {

    // 收货地址列表，ums_member_receive_address表
    @Setter
    @Getter
    List<MemberAddressVo> address;

    //所有选中的购物项
    @Setter @Getter
    List<OrderItemVo> items;

    //优惠券信息...
    @Setter @Getter
    Integer integration;

    //表示库存
    @Setter @Getter
    Map<Long,Boolean> stocks ;

    // BigDecimal total;//订单总额

    //在订单确认页，为了防止一个用户按多次提交订单，导致下单很多，在订单确认页每次过来都给它带一个唯一的令牌来进行防重
    @Setter @Getter
    String orderToken;//订单的防重令牌

    //计算获取商品的件数
    public Integer getCount(){
        Integer i = 0;
        if (items != null) {
            for (OrderItemVo item : items) {
                i+=item.getCount();
            }
        }
        return i;
    }

    //计算订单总额
    public BigDecimal getTotal() {
        BigDecimal sum = new BigDecimal("0");
        if (items != null) {
            for (OrderItemVo item : items) {
                BigDecimal multiply = item.getPrice().multiply(new BigDecimal(item.getCount().toString()));
                sum = sum.add(multiply);
            }
        }
        return sum;

    }
    BigDecimal payPrice; //应付价格

    //计算应付价格
    public BigDecimal getPayPrice() {
        return  getTotal();
    }




}
