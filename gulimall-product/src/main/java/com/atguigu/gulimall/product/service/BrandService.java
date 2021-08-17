package com.atguigu.gulimall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.BrandEntity;

import java.util.List;
import java.util.Map;

/**
 * 品牌
 *
 * @author hhf
 * @email hhf@gmail.com
 * @date 2020-04-01 22:30:38
 */
public interface BrandService extends IService<BrandEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void updateDateil(BrandEntity brand);

    List<BrandEntity> getBrandsByIds(List<Long> brandIds);
}

