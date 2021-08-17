package com.atguigu.gulimall.product.vo;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * 销售属性
 */
@ToString
@Data
public class SkuItemSaleAttrsVo {
    /**
     * 属性id
     */

    private Long attrId;
    /**
     * 属性名
     */
    private String attrName;
    /**
     * 商品版本的那些值信息，比如红色+24版
     */
    private List<AttrValueWithSkuIdVo> attrValues;

}
