package com.atguigu.common.to.mq;

import lombok.Data;

import java.util.List;

/**
 * 库存锁定的To
 */
@Data
public class StockLockedTo {

    private Long id; //库存工作单的id
//    private Long detailId;//工作详情的id信息
    private StockDetailTo detail;//工作详情的所有id信息
}
