package com.atguigu.gulimall.cart.controller;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.Cart;
import com.atguigu.gulimall.cart.vo.CartItem;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Controller
public class    CartController {

    @Autowired
    CartService cartService;


    /**
     * 获取到当前用户的所有购物项
     */
    @GetMapping("/currentUserCartItems")
    @ResponseBody
    public List<CartItem> getCurrentUserCartItems(){

        return cartService.getUserCartItems();//获取用户的购物车里面的数据
    }

    /**
     * 跳转到购物车列表页面获取购物车里面的所有数据
     * 浏览器有一个cookie，用来标识用户的身份，而且这个身份一般是一个月后才过期的
     * 如果第一次访问购物车，都会自动分配一个临时用户身份
     * 这个临时用户身份，浏览器都在保存着，每次购物车相关的请求操作都会带上这个临时用户身份
     * 如果是登录了，那么就是在session里面有
     * 如果没有登录，就按照cookie里面带来的临时用户身份来做
     * 如果没有临时用户身份，还要自动创建一个临时用户给浏览器
     * 也就是每一个请求都得检查这个浏览器是否已经登录了还是没有登录，因为要看这个浏览器要获取什么样的数据
     * 可以写一个拦截器去实现其功能
     * 在目标方法执行之前，先让拦截器先来获取一下当前浏览器的登录与不登录的状态信息
     * 如果浏览器没登录，而且cookie里面还没有临时用户身份，还可以给浏览器创建一个临时的用户身份
     * @return
     */
    @GetMapping("/cart.html")
    public String cartListPage(Model model) throws ExecutionException, InterruptedException {
       Cart  cart =  cartService.getCart();//获取购物车
        model.addAttribute("cart",cart);
        return "cartList";
    }

    /**
     * 添加商品到购物车
     * RedirectAttributes
     *      ra.addFlashAttribute() 将数据放到session中，可以在页面取出，但是只能取一次
     *      ra.addAttribute("skuId",skuId); 将数据放在路径的url后面
     *
     *     最终： 使用重定向解决购物车商品数量刷新页面会增加的问题
     * @return
     */
    @GetMapping("/addToCart")
    public String addToCart(@RequestParam("skuId") Long skuId, @RequestParam("num") Integer num, RedirectAttributes ra) throws ExecutionException, InterruptedException { //参数：哪个商品,数量
        //把一个商品添加到购物车
       cartService.addToCart(skuId,num);
        //为了防止添加购物车成功以后，出现刷新页面重复提交，使得商品数量有所增加，那么就一旦添加商品成功，就直接重定向到success页面
        ra.addAttribute("skuId",skuId);

        return "redirect:http://cart.gulimall.com/addToCartSuccess.html";
    }

    /**
     * 跳转到购物车成功页
     * 添加商品到购物车成功后的，为防止重复提交商品数量
     * 给页面放这个购物车。查出来的数据不在添加完商品到购物车之后放，
     * 添加完商品到购物车以后，重定向到页面，然后再查一遍，最后进行展示
     * 这样就能解决重复提交的问题
     * @return
     */
    @GetMapping("/addToCartSuccess.html")
    public String addToCartSuccessPage(@RequestParam("skuId") Long skuId, Model model){  //在重定向的时候，携带上商品的id
        //根据商品id再查一次购物车商品数量有多少个
       CartItem item = cartService.getCartItem(skuId);     //获取某一个指定的购物项\
          model.addAttribute("item",item);
        return "success";
    }

    /**
     *勾选购物车里面的购物项处理器
     * @param skuId
     * @param check
     * @return
     */
    @GetMapping("/checkItem")
    public String checkItem(@RequestParam("skuId") Long skuId,@RequestParam("check")Integer check){
        /**
         * skuId :是要勾选的商品
         * check：是否勾选
         */
        cartService.checkItem(skuId,check);
        return "redirect:http://cart.gulimall.com/cart.html";
    }

    /**
     * 改变购物车当中商品的数量
     */
    //countItem
    @GetMapping("/countItem")
    public String countItem(@RequestParam("skuId") Long skuId,@RequestParam("num")Integer num){
        /**
         * skuId：改变哪个商品
         * num：改变的数量
         */
        cartService.changeItemCount(skuId,num);
        return "redirect:http://cart.gulimall.com/cart.html";
    }

    /**
     * 删除购物车当中某一个购物项的请求处理
     */
    @GetMapping("/deleteItem")
    public String deleteItem(@RequestParam("skuId") Long skuId){
        cartService.deleteItem(skuId);
        return "redirect:http://cart.gulimall.com/cart.html";

    }
}
