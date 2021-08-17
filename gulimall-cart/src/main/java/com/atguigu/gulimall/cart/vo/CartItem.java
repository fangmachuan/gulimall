package com.atguigu.gulimall.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物项内容
 */

public class CartItem {
    private Long skuId; //商品的id
    private Boolean check = true; //商品是否被选中
    private String title;   //商品的标题
    private String image; //商品的图片
    private List<String> skuAttr;   //商品的套餐信息
    private BigDecimal price; //商品总价格
    private Integer count;  //商品数量
    private BigDecimal totalPrice; //商品的小计价格

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Boolean getCheck() {
        return check;
    }

    public void setCheck(Boolean check) {
        this.check = check;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<String> getSkuAttr() {
        return skuAttr;
    }

    public void setSkuAttr(List<String> skuAttr) {
        this.skuAttr = skuAttr;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * 动态计算购物项的商品价格
     * @return
     */
    public BigDecimal getTotalPrice() {
        //计算当前商品的单价乘以数量就是当前商品的总价格
        return this.price.multiply(new BigDecimal("" + this.count));
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
