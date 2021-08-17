package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.StockDetailTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.feign.ProductFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.vo.OrderItemVo;
import com.atguigu.gulimall.ware.vo.OrderVo;
import com.atguigu.gulimall.ware.vo.SkuHasStockVo;
import com.atguigu.gulimall.ware.vo.WareSkuLockVo;
import com.rabbitmq.client.Channel;
import lombok.Data;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Autowired
    WareSkuDao wareSkuDao;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    WareOrderTaskDetailService orderTaskDetailService;

    @Autowired
    WareOrderTaskService orderTaskService;

    @Autowired
    OrderFeignService orderFeignService;





    /**
     * 库存锁定释放
     * 1.库存自动解锁
     *
     * 先用这种方式解锁库存：
     *          2、下订单成功，库存锁定成功，但是接下来的其他业务调用失败，导致订单回滚
     *             之前锁定的库存就要在一段时间过后自动解锁。而这个解锁如果要用分布式事务seata来做太慢了，
     *               所以采用MQ的可靠消息+最终一致性方案（异步确保型）
     *      若订单失败
     *      锁库存失败导致的，也就是说有一个商品没锁住
     *
     * 只要解锁库存的消息失败，一定要告诉服务器此次解锁失败，这个消息不要删除，可以重试来解锁
     *                      那么就需要手动的启动ACK机制
     */


    /**
     *  库存解锁方法
     * @param skuId 解锁的哪个商品
     * @param wareId 当时在哪个仓库扣的库存
     * @param num   扣的数量
     * @param taskDetailId  库存工作单的id
     */
    private void unLockStock(Long skuId,Long wareId,Integer num,Long taskDetailId){
        //库存解锁
        wareSkuDao.unLockStock(skuId,wareId,num);
        //更新库存工作单的状态为：2（已解锁）
        WareOrderTaskDetailEntity entity = new WareOrderTaskDetailEntity();
        entity.setId(taskDetailId);
        entity.setLockStatus(2); //变为已解锁状态
        orderTaskDetailService.updateById(entity);
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        /**
         * wareId: 123,//仓库id
         *    skuId: 123//商品id
         */
        QueryWrapper<WareSkuEntity> queryWrapper = new QueryWrapper<>();
        String skuId = (String) params.get("key");
        if (!StringUtils.isEmpty(skuId)){
            queryWrapper.eq("sku_id",skuId);
        }
        String wareId = (String) params.get("wareId");
        if (!StringUtils.isEmpty(wareId)){
            queryWrapper.eq("ware_id",wareId);
        }

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                queryWrapper
        );

        return new PageUtils(page);
    }

    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum) {
        //1、判断如果还没有这个库存记录新增
        List<WareSkuEntity> entities = wareSkuDao.selectList(new QueryWrapper<WareSkuEntity>().eq("sku_id", skuId).eq("ware_id", wareId));
        if(entities == null || entities.size() == 0){
            WareSkuEntity skuEntity = new WareSkuEntity();
            skuEntity.setSkuId(skuId);
            skuEntity.setStock(skuNum);
            skuEntity.setWareId(wareId);
            skuEntity.setStockLocked(0);
            //TODO 远程查询sku的名字，如果失败，整个事务无需回滚
            //TODO 还可以用什么办法让异常出现以后不回滚  在高级篇用到
            try {
                R info = productFeignService.info(skuId);
                Map<String,Object> data = (Map<String, Object>) info.get("skuInfo");
                if(info.getCode() == 0){
                    skuEntity.setSkuName((String) data.get("skuName"));
                }
            }catch (Exception e){
            }
            wareSkuDao.insert(skuEntity);
        }else{
            wareSkuDao.addStock(skuId,wareId,skuNum);
        }

    }

    /**
     * 检查每个商品的库存
     * @param skuIds
     * @return
     */
    @Override
    public List<SkuHasStockVo> getSkusHasStock(List<Long> skuIds) {
        //逐一检查
        List<SkuHasStockVo> collect = skuIds.stream().map(skuId -> {
            SkuHasStockVo vo = new SkuHasStockVo();
            //查询当前sku的库存量
            //SELECT SUM(stock-stock_locked) FROM `wms_ware_sku` WHERE sku_id=1
           Long count = baseMapper.getSkuStock(skuId); //获取每一个sku的库存总量
            //按照这个计数count，就会判断是否有库存
            vo.setSkuId(skuId);
            vo.setHasStock(count==null?false:count>0); //有库存
            return vo;
        }).collect(Collectors.toList());

        return collect;
    }

    /**
     * (rollbackFor = NoStockException.class)
     * 默认只要是运行时异常 都会回滚
     * 为某个订单锁定库存
     *
     * 解锁库存要考虑以下场景：
     *      1、下订单成功，订单过期没有支付，被系统自动取消了，或者被用户手动取消订单，库存系统都要解锁库存
     *      2、下订单成功，库存锁定成功，但是接下来的其他业务调用失败，导致订单回滚
     *          之前锁定的库存就要在一段时间过后自动解锁。而这个解锁如果要用分布式事务seata来做太慢了，
     *          所以采用MQ的可靠消息+最终一致性方案（异步确保型）
     * @param vo
     * @return
     * WareSkuLockVo的
     *   private String orderSn; //订单号
     *
     *     private List<OrderItemVo> locks; //需要锁住的所有库存相关信息
     */
    @Transactional
    @Override
    public Boolean orderLockStock(WareSkuLockVo vo) {

        /**
         *库存一进来，准备锁库存，先保存库存工作单的详情，也即哪个订单号和订单id准备来锁库存了
         * 引入这个工作单是为了追溯哪个仓库哪一块商品都锁了多少，包括一旦出问题，可能还要人为的回滚也方便
         */
        WareOrderTaskEntity taskEntity = new WareOrderTaskEntity();
        //相当于是给哪个订单号锁的库存
        taskEntity.setOrderSn(vo.getOrderSn()); //订单号
        orderTaskService.save(taskEntity);

        //1.找到每个商品在哪个仓库都有库存
        List<OrderItemVo> locks = vo.getLocks();
        List<SkuWareHasStock> collect = locks.stream().map(item -> {
            SkuWareHasStock stock = new SkuWareHasStock();
            Long skuId = item.getSkuId();//想要锁的商品
            stock.setSkuId(skuId);
            stock.setNum(item.getCount());
            //查询这个商品在哪里有库存
            List<Long> wareIds = wareSkuDao.listWareIdHasSkuStock(skuId);
            stock.setWareId(wareIds);
            return stock;
        }).collect(Collectors.toList());

        //2 锁定库存核心方法
        for (SkuWareHasStock hasStock : collect) {
            //先来找一下，这些商品都在哪个仓库有库存
            Boolean skuStocked = false; //默认是没被锁住
            Long skuId = hasStock.getSkuId();
            List<Long> wareIds = hasStock.getWareId();
            //遍历每一个仓库
            if (wareIds ==null ||wareIds.size()==0){
                //没有任何仓库有这个商品的库存
                throw new NoStockException(skuId);
            }

            /**
             * 1、如果每一个商品都锁成功，就将当前商品锁定了几件的工作单详情记录发送给了MQ
             * 2、如果锁定失败。前面保存的工作单信息就回滚了，
             * 3、但是一回滚以后，还可能把消息发送出去MQ了，但是这样也其实没事，就算解锁库存，因为发的ID，最后得来拿id来查当前整了多少个库存
             *      但是一回滚了，就没这个id，也即没这个记录，就不用解锁了。但是这种也是不太合理的
             * 4.举个例子：比如1号商品在一号仓库锁了两件，还锁成功了，2号商品锁了一件在2号仓库锁成功了  结果3号商品锁一件在1号仓库锁失败了
             *        那么此时这个一锁失败以后，整个发出去的消息都带着id，这个发出去的id就会发现由于3号商品锁定失败，前面的都回滚了
             *        那么一回滚，在锁定库存那扣减了，但是在工作单却给回滚了，相当于就不知道此时锁了多少个商品了，因为按照id都查出不来了
             *         所以光发id还不够，应该发送整条完整的消息
             */
            //如果有仓库就进行锁定仓库并进行扣减
            for (Long wareId: wareIds){
                //锁的商品是哪个，想锁哪个仓库下的，锁几件？
                //成功就返回1，否则就是0
               Long count = wareSkuDao.lockSkuStock(skuId,wareId,hasStock.getNum());
               if (count==1){
                   //说明锁住了,就没有必要去锁其他仓库了，
                   // 然后发送一个消息告诉MQ库存锁定成功，然后就触发自动解锁逻辑,
                   //然后一旦锁成功了，就保存这个锁成功的详情
                   /**
                    * 参数解释：
                    * 第一个参数：库存详情工作单的id是自增的不填。
                    * 第二个参数； 哪个商品的id
                    * 第三个参数： 商品名字没有传
                    * 第四个参数：   锁几件商品
                    * 第五个参数： 工作单的id
                    * 第六个参数： 在哪个仓库锁了商品
                    * 第七个参数： 锁定的状态:默认已填写为1，也即锁定成功了
                    *
                    */
                   WareOrderTaskDetailEntity entity = new WareOrderTaskDetailEntity(null, skuId, "", hasStock.getNum(), taskEntity.getId(), wareId, 1);
                   //保存进来了以后，相当于每锁一个库存，在数据库里面都会保存当前工作单的详情
                   orderTaskDetailService.save(entity);
                   //保存完了以后，再来使用MQ来发送消息,告诉MQ，锁了一些库存
                   /**
                    *    参数1：发送给哪个交换机
                    *  参数2：用的路由建是什么
                    *   参数3：发的消息内容是什么    发的是哪一个库存工作单到低锁了多少个商品信息
                    */
                   StockLockedTo lockedTo = new StockLockedTo();//给MP发送消息的封装的数据
                   //只要有一个锁定成功，就把工作单id外还把当前锁定的详细信息发给MQ，也防止前面的数据回滚了找不到数据
                   lockedTo.setId(taskEntity.getId());
                   StockDetailTo stockDetailTo = new StockDetailTo();
                   BeanUtils.copyProperties(entity,stockDetailTo);
                   lockedTo.setDetail(stockDetailTo); //
                   rabbitTemplate.convertAndSend("stock-event-exchange","stock.locked",lockedTo); //想要触发自动解锁的逻辑
                   skuStocked = true;
                   break;
               }else {
                   //当前仓库锁失败，继续重试下一个仓库
               }
            }
            if (skuStocked == false){
                //当前商品所有仓库都没有锁住
                throw new NoStockException(skuId);
            }
        }
        //3.肯定全部都是锁定成功的
        return true;
    }

    /**
     * 只要解锁库存的消息失败，一定要告诉服务器此次解锁失败，这个消息不要删除，可以重试来解锁
     * 那么就需要手动的启动ACK机制
     * 解锁库存的方法
     * @param to
     */
    @Override
    public void unlockStock(StockLockedTo to) {

        StockDetailTo detail = to.getDetail();//每一个详细信息
        Long detailId = detail.getId();//工作单详情的id
        //解锁
        //1.查询数据库关于这个订单的锁定库存信息，
        // 如果数据库有，就需要解锁
        //如果数据库没有 也即工作单都没有，那么库存锁定失败，整个库存也回滚了导致的没有，这种情况下无需解锁
        //解锁订单情况：
        //  1.，没有这个订单，必须解锁
        //  2. 有这个订单 不是解锁库存
        //先看订单状态：已取消订单，解锁库存
        //如果是没有取消，就不能解锁库存
        WareOrderTaskDetailEntity byId = orderTaskDetailService.getById(detailId);//查询库存工作单详情信息
        if (byId!=null){
            //解锁，如果库存工作单在数据库有，那么说明库存是成功的，但是也不能上来就解锁，如果有的话，只能表明库存服务自己在锁库存的时候成功了，一切成功返回出去的
            //但是要看整个订单有没有成功
            Long id = to.getId();//根据这个id来查询出订单详情信息
            WareOrderTaskEntity taskEntity = orderTaskService.getById(id);//查出工作单的信息
            String orderSn = taskEntity.getOrderSn();//根据订单号查询订单的状态，只要订单是已取消状态，才能解锁库存
            //在解锁之前，先来远程查询订单的状态
            R r = orderFeignService.getOrderStatus(orderSn);
            if (r.getCode() == 0){
                //订单数据返回成功
                OrderVo data = r.getData(new TypeReference<OrderVo>() { //得到订单数据
                });
                //这个订单是取消状态就解锁库存
                //那就算订单不存在。原因就是库存服务已经锁好库存了，库存工作单的数据都有，但是订单调了其他方法，
                // 订单出现问题了，导致订单回滚了，此时这种情况还要进行解锁库存
                if (data == null || data.getStatus() == 4){ //订单不存在，或者订单被取消这两种情况都得解锁库存
                    //解锁库存成功
                    if (byId.getLockStatus() == 1){  //当前库存的状态是1的话也即属于已锁定的话，但是未解锁才可以解锁
                        //如果当前的这个锁定状态不是已解锁的话，才能进行解锁
                        //什么时候能解锁呢？
                        // 要在库存工作单的状态是已锁定状态才能解锁，如果已经解过锁了就不用解锁了，或者是已经把库存都真是扣减了，那就更加不能解锁了
                        unLockStock(detail.getSkuId(),detail.getWareId(),detail.getSkuNum(),detailId);
                    }
                }
                //失败
            }else {
                //远程服务返回的状态码不是0，也就是远程服务失败，就抛一个异常,来告诉远程服务失败，然后也重新解锁库存，并且这个消息不能算消费成功
                throw  new RuntimeException("远程服务失败");
            }
        }else {
            //如果没有就无需解锁,相当于消息也无需消费了
            // 如果没有的话就说明库存自己本身不成功，自己都回滚了，所以自己都自动解锁了
        }
    }

    /**
     * 防止订单服务卡顿，导致订单状态消息一直改不了，库存消息优先到期 ，结果查订单状态肯定是新建状态，什么都不做就走了（也即也消费了）
     * 导致这个卡顿的订单，永远不能解锁库存
     * 处理：订单即使取消了，也给MQ发一条消息到库存服务，让他来解锁库存
     * @param orderTo
     */
    @Transactional
    @Override
    public void unlockStock(OrderTo orderTo) {
        String orderSn = orderTo.getOrderSn();
        //解库存，按照库存工作单按照订单号找到库存工作单的id，再来找到之前哪些商品都已经被锁定了,而且确定这个商品没有被解锁过的，
        // 假设这个订单都是这个库存服务都帮忙解锁过了，再来解锁一遍这就造成了重复扣库存
        //所以要查一下库存最新的解锁状态才能防止重复解锁库存
        WareOrderTaskEntity task = orderTaskService.getOrderTaskByOrderSn(orderSn);//拿到库存工作单来查询
        Long id = task.getId();
        //拿到库存工作单的id,找到当时所有没有解锁的库存商品进行解锁
        List<WareOrderTaskDetailEntity> entities = orderTaskDetailService.list(
                new QueryWrapper<WareOrderTaskDetailEntity>()
                        .eq("task_id", id)
                        .eq("lock_status", 1));//为1，就是刚新建进来的，还没有解锁，如果解锁了就会成为2

        //调用解锁方法 Long skuId,Long wareId,Integer num,Long taskDetailId
        /**
         * 你要解的哪个商品（skuId）
         *  哪个仓库解锁（wareId）
         *  解锁的件数（num）
         *  包括你当前工作单的详情id信息(taskDetailId)
         */
        for (WareOrderTaskDetailEntity entity : entities) {
            unLockStock(entity.getSkuId(),entity.getWareId(),entity.getSkuNum(),entity.getId());
        }
    }

    @Data
    class SkuWareHasStock{
        private Long skuId;//当前商品的id
        private Integer num; //锁多少件
        private List<Long> wareId;//都在哪些仓库有库存
    }

}