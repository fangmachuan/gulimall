package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.BrandDao;
import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.service.BrandService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("brandService")
public class BrandServiceImpl extends ServiceImpl<BrandDao, BrandEntity> implements BrandService {


    //注入关联表
    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        //1.获取key,进行模糊查询
        String key = (String) params.get("key");
        QueryWrapper<BrandEntity> queryWrapper = new QueryWrapper<>();
        if (!StringUtils.isEmpty(key)){  //传过来的值不是空的，那么就要进行模糊检索
            //key那输入的是id的值，or或者key是输入的是名字
            queryWrapper.eq("brand_id",key).or().like("name",key);

        }
        IPage<BrandEntity> page = this.page(
                new Query<BrandEntity>().getPage(params),queryWrapper

        );

        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void updateDateil(BrandEntity brand) {
        //保证冗余字段的数据一致
        this.updateById(brand); //先把自己表里面的数据更新掉
        //然后再来判断，如果此次更新的品牌里面有更新品牌名字
        if (!StringUtils.isEmpty(brand.getName())){  //如果品牌名不为空，那么其他表里面也要保存品牌名
            //同步更新其他关联表中的数据
            categoryBrandRelationService.updateBrand(brand.getBrandId(),brand.getName()); //关联表动态更新品牌的信息


            //TODO 更新其他关联
        }
    }

    @Override
    public List<BrandEntity> getBrandsByIds(List<Long> brandIds) {

        return  baseMapper.selectList(new QueryWrapper<BrandEntity>().in("brand_id",brandIds));
    }

}