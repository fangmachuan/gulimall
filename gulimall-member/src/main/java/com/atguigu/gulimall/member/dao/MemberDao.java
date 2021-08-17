package com.atguigu.gulimall.member.dao;

import com.atguigu.gulimall.member.entity.MemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员
 * 
 * @author hhf
 * @email hhf@gmail.com
 * @date 2020-04-01 22:44:40
 */
@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
