package com.atguigu.gulimall.product.vo;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * 基本属性
 */
@ToString
@Data
public class SpuItemAttrGroupVo {
    private String groupName; //分组的名字
    //当前分组下对应相应的属性信息
    private List<Attr> attrs;
}
