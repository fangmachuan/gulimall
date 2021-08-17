package com.atguigu.gulimall.product.app;

import java.util.Arrays;
import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.common.utils.R;



/**
 * 商品三级分类
 *
 * @author hhf
 * @email hhf@gmail.com
 * @date 2020-04-01 22:30:38
 */
@RestController
@RequestMapping("product/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    /**
     * 查出所有分类以及子分类，以树形结构组装起来
     */
    @RequestMapping("/list/tree")
   // @RequiresPermissions("product:category:list")
    public R list(){
      List<CategoryEntity> entities = categoryService.listWithTree();//一次性查出所有分类数据，并且组装成父子结构

        return R.ok().put("data", entities);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{catId}")
   // @RequiresPermissions("product:category:info")
    public R info(@PathVariable("catId") Long catId){
		CategoryEntity category = categoryService.getById(catId);

        return R.ok().put("data", category);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    //@RequiresPermissions("product:category:save")
    public R save(@RequestBody CategoryEntity category){
		categoryService.save(category);

        return R.ok();
    }

    @RequestMapping("/update/sort")
    // @RequiresPermissions("product:category:update")
    public R updateSort(@RequestBody CategoryEntity[] category){
     //   categoryService.updateById(category);
        categoryService.updateBatchById(Arrays.asList(category));//按照ID进行批量修改
        return R.ok();
    }



    /**
     * 修改
     */
    @RequestMapping("/update")
   // @RequiresPermissions("product:category:update")
    public R update(@RequestBody CategoryEntity category){
		categoryService.updateCascade(category); //进行级联更新

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
  //  @RequiresPermissions("product:category:delete")
    public R delete(@RequestBody Long[] catIds){

		//categoryService.removeByIds(Arrays.asList(catIds));
        //1、检查当前要删除的菜单，是否被别的地方引用
        categoryService.removeMenuByIds(Arrays.asList(catIds));  //批量删除
        return R.ok();
    }

}
