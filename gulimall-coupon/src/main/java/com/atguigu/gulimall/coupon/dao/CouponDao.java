package com.atguigu.gulimall.coupon.dao;

import com.atguigu.gulimall.coupon.entity.CouponEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券信息
 * 
 * @author hhf
 * @email hhf@gmail.com
 * @date 2020-04-01 22:49:05
 */
@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {
	
}
