package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.Cart;
import com.atguigu.gulimall.cart.vo.CartItem;
import com.atguigu.gulimall.cart.vo.SkuInfoVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    ThreadPoolExecutor executor;

    //购物车所有的建，都有一个前缀
    private final String CART_PREFIX = "gulimall:cart:";

    /**
     * 给购物车里面添加商品
     * @param skuId  某个商品
     * @param num   商品的数量
     * @return
     * 如果远程查询比较慢，比如方法当中有好几个远程查询，都要好几秒以上，等整个方法返回可能就要很久，这块你是怎么处理的？
     * 那么为了能提升远程查询的效率，可以使用线程池的方式
     * 要做的操作就是将所有的线程全部放到自己手写的线程池里面
     * 所以每一个服务都要配置一个自己的线程池
     * 完全使用线程池来控制住所有的请求
     */
    @Override
    public CartItem addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException {
        //获取到要操作的购物车
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        //如果购物车当中已经有了相同的商品，就无需再进行一下操作，直接在数量上增加就行了
        String res = (String) cartOps.get(skuId.toString());//商品的id作为键，先去Redis里面查看有没有这个相同的商品
        if (StringUtils.isEmpty(res)){ //如果返回的是空的，说明购物车里面没有这个商品 ，就得执行添加新商品的方法
            //2.添加新商品到购物车
            //第一个异步任务
            CartItem cartItem = new CartItem();   //购物车里面每一个都是一个购物项.封装购物车的内容
            CompletableFuture<Void> getSkuInfoTask = CompletableFuture.runAsync(() -> {
                //1.远程查询当前要添加的商品的信息
                //添加哪个商品到购物车，先查询这个商品的信息
                R skuInfo = productFeignService.getSkuInfo(skuId);
                //拿到商品详细信息的真正内容
                SkuInfoVo data = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {//获取返回的数据，因为远程服务，返回的sku的详细信息会封装在skuinfo当中
                });
                cartItem.setCheck(true);//既然添加了这个商品，那么默认的选中状态就是true
                cartItem.setCount(num);//当前添加的商品数量
                cartItem.setImage(data.getSkuDefaultImg());//当前商品的图片信息
                cartItem.setTitle(data.getSkuTitle());//当前商品的标题信息
                cartItem.setSkuId(skuId);//查的是哪个商品，那么这个商品的id就是哪个
                cartItem.setPrice(data.getPrice());//当前商品的价格信息
            },executor);
            //3。第二个异步任务，远程查询sku的组合信息
            CompletableFuture<Void> getSkuSaleAttrValues = CompletableFuture.runAsync(() -> {
                List<String> values = productFeignService.getSkuSaleAttrValues(skuId);//按照id来差
                cartItem.setSkuAttr(values);//远程查出当前商品的sku的销售属性组合信息，需要在购物车里面进行展示
            }, executor);
            //这两个异步任务都完成了以后才能在redis里面存放数据
            CompletableFuture.allOf(getSkuInfoTask,getSkuSaleAttrValues).get();
            //把购物项的数据保存到redis里面
            String s = JSON.toJSONString(cartItem);
            cartOps.put(skuId.toString(), s);//添加商品到购物车
            return cartItem;
        }else { //如果购物车有这个相同的商品
            //做相应商品的数量叠加，也即从上面res那新添加的商品的数量结果加上这次购物车已经存在的商品数量
            CartItem cartItem = JSON.parseObject(res, CartItem.class);
            cartItem.setCount(cartItem.getCount()+num);//原来数量加上传过来的数量
            //更新redis
            cartOps.put(skuId.toString(),JSON.toJSONString(cartItem));
            return cartItem;
        }
        }

    /**
     * 获取购物车里面的某个购物项
     * @param skuId
     * @return
     */
    @Override
    public CartItem getCartItem(Long skuId) {
        //根据现在登录状态，得知操作哪个购物车
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String str = (String) cartOps.get(skuId.toString());//获取当前商品的购物项
        CartItem cartItem = JSON.parseObject(str, CartItem.class);
        return cartItem;
    }

    /**
     * 获取整个购物车，也就是在商品页，点击购物车那块
     * @return
     */
    @Override
    public Cart getCart() throws ExecutionException, InterruptedException {
        Cart cart = new Cart();
        //1.购物车的获取操作，得先分登录后的购物车还是没登录的购物车
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();//获取到共享的用户信息
        if (userInfoTo.getUserId()!=null){ //登录后的购物车
            String cartKey =CART_PREFIX+userInfoTo.getUserId();
            String tempCartKey = CART_PREFIX + userInfoTo.getUserKey();
            //如果临时购物车的有数据但还没有进行合并
            //先判断临时购物车有没有数据
            List<CartItem> tempCartItems = getCartItems(tempCartKey);//拿到临时购物车的数据
            if (tempCartItems!=null){ //说明临时购物车有数据,就需要合并登录和没有登录的购物车数据
                for (CartItem item : tempCartItems) { //拿到所有的临时购物车数据，添加到登录后的购物车里面来
                    //调用addToCart()这个方法，是当前登录状态，给购物车里面填离线和在线购物车，会判断成功是登录状态的，所以这个方法用的都是登录后的购物车信息
                    //所以这个方法就会给
                    addToCart(item.getSkuId(),item.getCount()); //合并临时和登录后的购物车
                }
                //合并完成后，还要清除临时购物车的数据
                clearCart(tempCartKey);
            }
            //合并操作完成后，再来获取登录后的购物车【包含合并来的临时购物车数据和登录后的购物车数据】
            List<CartItem> cartItems = getCartItems(cartKey);
            cart.setItems(cartItems);//把整个临时和登录后的购物车数据放入刀片
        }else { //没有登录的购物车,拿到没有登录后的购物车数据
            String cartKey =CART_PREFIX+userInfoTo.getUserKey();
            //获取临时购物车的所有购物项
            List<CartItem> cartItems = getCartItems(cartKey);
            cart.setItems(cartItems);//返回整个购物车
        }
        return cart;
    }

    /**
     * 获取到我们要操作的购物车
     * 返回购物车的操作方法
     * 以后想要调用购物车就可以直接调用这个方法
     */
    private BoundHashOperations<String, Object, Object> getCartOps() {
        //1.要想知道用户是登录了还是没有登录来进行添加购物车，请求要想放给controller的这个方法，拦截器就已经执行了，拦截器会得到用户的信息
        //在这里来得到用户的登录信息,只要是同一次请求，在任何位置都能得到
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();//获取到共享的用户信息
        String cartKey = "";
        //如果userinfo已经登录了，肯定是给它登录后的购物车添加商品，只有没登录，那么所有的操作才在临时购物车里面
        if (userInfoTo.getUserId()!=null){ //说明登陆了，此时就用登录后的这个key
            //gulimall:cart:1 这是登录后的key
            cartKey = CART_PREFIX+userInfoTo.getUserId();
        }else { //如果用户没登录，就用临时的购物车
            cartKey = CART_PREFIX+userInfoTo.getUserKey();
        }
        //将一个商品，添加到相应的购物车
        //添加到购物车，应该还有两个操作
        //1.购物车里面之前若有了这个商品，相当于redis当中保存了，就只需要把redis里面的购物车当前这个商品数量改一下
        //2.如果没有这个商品,那么就要给redis里面新增这个商品信息
        //绑定一个hash操作，以后所有的对redis的增删改查，都是针对这个key的增删改查
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(cartKey);//获取哪个用户的购物车信息
        return operations;
    }

    /**
     * 获取指定用户（临时用户/登录后的用户）购物车里面的数据
     * @return
     */
    private List<CartItem> getCartItems(String cartKey){
        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(cartKey);//绑定获取没有登录的购物车
        List<Object> values = hashOps.values();//拿到购物车当中的所有购物项
        if (values!=null && values.size()>0){
            List<CartItem> collect = values.stream().map((obj) -> {
                String str = (String) obj;
                CartItem cartItem = JSON.parseObject(str, CartItem.class);
                return cartItem;
            }).collect(Collectors.toList());
           return collect;
        }
        return null;
    }

    /**
     * 清空购物车方法
     */
    @Override
    public void clearCart(String cartKey){
       redisTemplate.delete(cartKey);

    }

    @Override
    public void checkItem(Long skuId, Integer check) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();//获取哪个用户的购物车
        //先看看操作的是哪个购物车
        CartItem cartItem = getCartItem(skuId);//得到相应的购物车当中的购物项
        cartItem.setCheck(check==1?true:false); //设置最新的状态信息
        //重新序列化存到redis
        String s = JSON.toJSONString(cartItem);
        cartOps.put(skuId.toString(),s);//key就是当前购物项的id，值就是修改购物项状态后的新值

    }

    /**
     * 修改购物车当中购物项的数量
     * @param skuId
     * @param num
     */
    @Override
    public void changeItemCount(Long skuId, Integer num) {
        //先拿到购物项原本的信息
        CartItem cartItem = getCartItem(skuId);
        cartItem.setCount(num);

        //再拿到购物车，重新保存到redis，
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();//根据当前登录状态来获取到购物车
        //获取到以后，改变当前商品的id作为key和改好后的数量作为值，再保存到redis当中
        cartOps.put(skuId.toString(),JSON.toJSONString(cartItem));
    }

    /**
     * 在购物车当中根据商品id来删除指定的购物项
     * @param skuId
     */
    @Override
    public void deleteItem(Long skuId) {
        //拿到当前购物车的操作
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.delete(skuId.toString());
    }

    /**
     * 获取到当前用户选中的所有购物项
     */
    @Override
    public List<CartItem> getUserCartItems() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();//获取当前用户信息
        if (userInfoTo.getUserId()==null){//没有登录
            return null;
        }else { //登录了，就给它获取登录后的购物车数据
            //操作哪个购物车，先需要传一个key
           String cartKey = CART_PREFIX+userInfoTo.getUserId();
           //去redis里面获取购物车里面的所有购物项
            List<CartItem> cartItems = getCartItems(cartKey);
            //再获取redis里面购物车的所有购物项当中已被选中的购物项
            List<CartItem> collect = cartItems.stream().filter(item -> item.getCheck())
                    .map(item->{
                        //因为这个价格很可能是以前的价格，所以要更新最新的价格进去
                        //需要远程查询商品服务，当前这个价格是多少
                        R price = productFeignService.getPrice(item.getSkuId());//查询商品的价格
                        String data = (String) price.get("data");
                        item.setPrice(new BigDecimal(data
                        ));
                        return item;
                    })
                    .collect(Collectors.toList());//获取被选中的购物项

            return  collect;//获取某个登录用户的购物车所有数据

        }


    }
}
