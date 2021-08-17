package com.atguigu.gulimall.search.vo;

import lombok.Data;

import java.util.List;

/**
 * 将所有的检索条件封装到这个SearchParam类上
 */
@Data
public class SearchParam {

    private String keyword; //页面传递过来的全文匹配关键字
    private Long catalog3Id; //页面传递过来的三级分类id
    private String sort; //排序条件

    private Integer hasStock ;//是否只显示有货 0无库存，1有库存
    private String skuPrice;//价格区间查询
    private List<Long> brandId; //按照品牌进行查询，可以多选
    private List<String> attrs; //按照属性进行筛选
    private Integer pageNum = 1;//页码

    private String _queryString;//原生的所有查询条件



}
