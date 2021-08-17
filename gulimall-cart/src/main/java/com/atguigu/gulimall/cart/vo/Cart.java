package com.atguigu.gulimall.cart.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 整个购物车
 * 需要计算的属性，必须重写他的get方法，保证每次获取属性都会进行计算
 */
public class Cart {

    List<CartItem> items; //购物车里面有很多的购物项

    private Integer countNum;//商品数量

    private Integer countType;//商品类型数量

    private BigDecimal totalAmount;//商品总购物车的价

    private BigDecimal reduce = new BigDecimal("0.00");//减免价格

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    /**
     * 计算商品的总量
     * @return
     */
    public Integer getCountNum() {
       int  count = 0;
       if (items!=null&&items.size()>0){ //说明购物车里面有购物项
           for (CartItem item : items) {
               count+=item.getCount();
           }
       }
        return count;
    }

    /**
     * 商品有多少个类型
     * @return
     */
    public Integer getCountType() {
        int count = 0;
        if (items != null && items.size() > 0) {
            for (CartItem item : items) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * 计算总价格
     * 先遍历每一个商品的总额，再减去需要减掉的金额
     * @return
     */
    public BigDecimal getTotalAmount() {
        BigDecimal amount = new BigDecimal("0");
        //1、计算购物项总价
        if (items != null && items.size() > 0) { //遍历所有的商品项
            for (CartItem item : items) {
                if(item.getCheck()){
                    BigDecimal totalPrice = item.getTotalPrice();
                    amount = amount.add(totalPrice);
                }
            }
        }

        //2、减去优惠总价
        BigDecimal subtract = amount.subtract(getReduce());
        //得到最终的总价，并且返回
        return subtract;
    }


    public BigDecimal getReduce() {
        return reduce;
    }

    public void setReduce(BigDecimal reduce) {
        this.reduce = reduce;
    }
}
