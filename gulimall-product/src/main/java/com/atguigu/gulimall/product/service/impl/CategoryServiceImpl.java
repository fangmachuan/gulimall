package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

//    @Autowired
//    CategoryDao categoryDao;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Autowired
    RedissonClient redisson;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 查出所有分类，并且组装成父子结构
     *
     * 最终实现的效果就是：只发了一个sql语句，查出所有的菜单，再利用递归的方式来组合出父子菜单
     * @return
     */
    @Override
    public List<CategoryEntity> listWithTree() {

        //1、查出所有分类
        List<CategoryEntity> entities = baseMapper.selectList(null);

        //2、组装成父子的树形结构
        //2.1)、找到所有的一级分类
        List<CategoryEntity> level1Menus = entities.stream().filter(categoryEntity ->
            categoryEntity.getParentCid() == 0
        ).map((menu)->{     //第一个参数是：将找到的当前菜单的所有子菜单，第二个参数是：在entities里面找到menu的子菜单
            menu.setChildren(getChildrens(menu,entities));  //当前菜单改了以后，保存当前菜单的子分类
            return menu; //把当前菜单返回回去
            //如果把父菜单找到了，父菜单还需要进行排序
            //如何排序？第一个参数是：传入前面的菜单，第二个参数是：传入后面的菜单，然后两个进行对比
        }).sorted((menu1,menu2)->{  //将当前映射好的这些菜单进行排序
            //每一个菜单都有Sort()这个方法，这个方法就是它的排序字段，让第一个的排序顺序减去第二个参数的排序顺序，给他们一个最终的升降序的值
               //返回对比的结果
            return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList()); //将所有的数据都收集到level1Menus里面
        return level1Menus;

    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO  1、检查当前要删除的菜单，是否被别的地方引用

        baseMapper.deleteBatchIds(asList);
    }

    /**
     * 收集三级分类的完整的路径
     * @param catelogId
     * @return
     */
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(catelogId, paths);//将所有的路径收集过来
        Collections.reverse(parentPath);
        return  parentPath.toArray(new Long[parentPath.size()]);
    }

    /**
     * 级联更新所有关联的数据
     * @param category
     */
//    @Caching(evict = {
//            @CacheEvict(value = "category",key = "'getLevel1Categorys'"),
//            @CacheEvict(value = "category",key = "'getCatalogJson'"),
//    })
    @CacheEvict(value = "category",allEntries = true)
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        //先更新自己
        this.updateById(category);
        //级联更新
        categoryBrandRelationService.updateCategory(category.getCatId(),category.getName());
    }

    /**
     *  @Cacheable ：
     *  代表当前方法的结果是可以放入缓存的，如果缓存中有，那么方法都不用调用了，如果缓存中没有，就会调用方法，最后将方法的结果放入缓存
     */
    //每一个需要缓存的数据，都要来指定放入到哪个名字的缓存，最好按照业务类型分名字
    @Cacheable(value = {"category"},key = "#root.method.name",sync = true)
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        System.out.println("getLevel1Categorys.....");
        List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));

        return categoryEntities;
    }

    @Cacheable(value = "category",key = "#root.methodName")
    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson() {
        //预先查出所有的分类，然后保存起来
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        //查出所有一级分类
        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        //封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //查到二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            List<Catelog2Vo> catelog2Vos = null;
            if (categoryEntities != null) {
                catelog2Vos = categoryEntities.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //查出三级分类
                    List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                    if (level3Catelog != null) {
                        List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(collect);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2Vos;
        }));

        return parent_cid;

    }

    //把三级分类的数据放入缓存中
   // @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson2() {

        /**
         * 1.空结果缓存，解决缓存穿透问题
         * 2.设置过期时间（并且能加随机值的），解决缓存雪崩问题
         * 3.加锁，来解决缓存击穿问题
         */
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");

        if (StringUtils.isEmpty(catalogJSON)) {//说明缓存中没有数据
            System.out.println("缓存不命中....查询数据库....");
            Map<String, List<Catelog2Vo>> catalogJsonFromDb = getDataFromDb(); //从数据库查询出来数据

            return catalogJsonFromDb;
        }
        System.out.println("缓存命中....直接返回....");
        //从缓存中拿到的数据转换为指定的对象
        Map<String,List<Catelog2Vo>> result = JSON.parseObject(catalogJSON,new TypeReference<Map<String, List<Catelog2Vo>> >(){});
        return result;
    }

    /**
     * 加分布式锁
     *
     * 缓存里面的数据如何和数据库保持一致
     * 缓存数据一致性问题
     * @return
     */
    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithRedissonLock() {
        //占分布式锁，去redis占坑
        RLock lock = redisson.getLock("catalogJson-lock");
        lock.lock();
        Map<String,List<Catelog2Vo>> dataFromDb;
        try {
            dataFromDb = getDataFromDb();
        }finally {
            lock.unlock();
        }
        return dataFromDb;
    }


    private Map<String, List<Catelog2Vo>> getDataFromDb() {
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        if (!StringUtils.isEmpty(catalogJSON)) { //缓存中有数据,直接返回
            Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
            });
            return result;
        }
        System.out.println("查询了数据库.....");
        //预先查出所有的分类，然后保存起来
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        //查出所有一级分类
        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        //封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //查到二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            List<Catelog2Vo> catelog2Vos = null;
            if (categoryEntities != null) {
                catelog2Vos = categoryEntities.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //查出三级分类
                    List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                    if (level3Catelog != null) {
                        List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(collect);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2Vos;
        }));

        //先将查询到的数据转为JSON，再把数据保存到缓存中,
        String s = JSON.toJSONString(parent_cid);
        redisTemplate.opsForValue().set("catalogJSON", s, 1, TimeUnit.DAYS);
        return parent_cid;
    }


    //从数据库查询并封装整个分类数据
    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithLocalLock() {

        synchronized (this){
            //得到锁以后，先看看缓存当中有没有数据
            return getDataFromDb();
        }


    }

    private List<CategoryEntity> getParent_cid( List<CategoryEntity> selectList,Long parent_cid) {
        List<CategoryEntity> collect = selectList.stream().filter(item -> item.getParentCid() == parent_cid).collect(Collectors.toList());
        //  return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
        return  collect;

    }

    private List<Long> findParentPath(Long catelogId, List<Long> paths){
        //收集当前节点ID
        paths.add(catelogId);
        //查出当前分类id，它的详情
        CategoryEntity byId = this.getById(catelogId);//用当前的id查出当前分类的信息
        //如果当前分类还有父分类，那么继续向上找到它的父分类
        if (byId.getParentCid()!=0){  //有效的父分类
            //继续依次往上来找，递归找，每次找到的数据都收集这些数据
            findParentPath(byId.getParentCid(),paths);//拿到父亲id
        }
        return paths;
    }

    /**
     *递归查找所有菜单的子菜单
     * @param root 当前菜单
     * @param all   从哪里获取它的子菜单 （所有菜单）
     *              从all里面找到这个root菜单的子菜单
     * @return
     */
    private List<CategoryEntity> getChildrens(CategoryEntity root,List<CategoryEntity> all){
        //整个这个的菜单，就是我们要用的子菜单
        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            //当前菜单的父ID如果等于指定的菜单的id，那么说明当前菜单就是它的子菜单
            return categoryEntity.getParentCid() == root.getCatId();
            //每一个子菜单可能还会有子菜单，包括这些子菜单还是需要排序的
        }).map(categoryEntity -> { //将菜单里面每一个东西都重新映射一下
            //第一个参数：当前的这个菜单，第二个参数：在all里面找到categoryEntity的子菜单
            categoryEntity.setChildren(getChildrens(categoryEntity,all)); //为每一个当前的这个菜单找到它的子菜单
            return categoryEntity; //找到以后，将这个菜单进行返回
            //菜单还是需要排序
        }).sorted((menu1,menu2)->{ //前面的菜单和后面的菜单
            return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());  //排序的规则是前面菜单的字段减去后面菜单的字段
        }).collect(Collectors.toList());//收集所有的菜单

        //找到子菜单，并且排好序以后，将他们收集返回
        return children;
    }

}