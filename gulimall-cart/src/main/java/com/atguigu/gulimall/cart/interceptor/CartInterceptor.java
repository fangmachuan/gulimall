package com.atguigu.gulimall.cart.interceptor;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.UUID;

/**
 * 拦截器的功能就是：
 *  在执行目标方法之前，先来判断用户的登录状态
 *
 *  保证购物车所有请求一进来，先能获取到用户的信息，无论是临时的还是登录后的用户信息
 *
 */
public class CartInterceptor implements HandlerInterceptor {

    public static ThreadLocal<UserInfoTo> threadLocal = new ThreadLocal<>();

    /**
     * 在目标方法执行之前拦截
     * 判断用户是否已经登录
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserInfoTo userInfoTo = new UserInfoTo();//登录了就有用户的id，没有登录就有用户的临时身份
        //查看当前用户有没有登录，
        HttpSession session = request.getSession();
        MemberRespVo  Member= (MemberRespVo) session.getAttribute(AuthServerConstant.LOGIN_USER);//获取当前登录的用户信息
        //不管用户登录了还是没有登录
        if (Member!=null){//说明用户登录了
            userInfoTo.setUserId(Member.getId());//就把用户的id设置进去
        }
        //每次发请求，识别临时用户还是登录后的用户，是从cookie里面识别的，所以每次请求来可以获取cookie里面的数据
        Cookie[] cookies = request.getCookies();//获取指定的cookie
        if (cookies!=null && cookies.length>0){ //说明cookie里面有数据
            //遍历cookie，获取指定的cookie信息
            for (Cookie cookie : cookies) {
                //先看看有没有这个系统的临时用户身份
                String name = cookie.getName();//获取cookie的名字
                if (name.equals(CartConstant.TEMP_USER_COOKIE_NAME)){ //如果有这个名字的cookie，那么就相当于拿到这个cookie
                    userInfoTo.setUserKey(cookie.getValue());
                    userInfoTo.setTempUser(true);
                }
            }
        }
        if (StringUtils.isEmpty(userInfoTo.getUserKey())){ //说明没有临时用户
            //那么就自定义一个临时的用户
            String uuid = UUID.randomUUID().toString();
            userInfoTo.setUserKey(uuid);
        }
        //目前方法执行之前
        threadLocal.set(userInfoTo);
        return true;  //来带目标方法全部都放行
    }

    /**
     * 目标方法执行之后，
     * 分配临时用户，让浏览器保存
     * 这样以后无论是调用购物车的任意方法，都可以很方便的获取到用户信息
     * @param request
     * @param response
     * @param handler
     * @param modelAndView
     * @throws Exception
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        UserInfoTo userInfoTo = threadLocal.get();
        //如果没有临时用户，一定保存一个临时的用户
        if (!userInfoTo.isTempUser()){//如果默认是没有临时用户信息
            //持续延长临时用户的过期时间
            Cookie cookie = new Cookie(CartConstant.TEMP_USER_COOKIE_NAME, userInfoTo.getUserKey());
            cookie.setDomain("gulimall.com");//放大作用域
            cookie.setMaxAge(CartConstant.TEMP_USER_COOKIE_TIMEOUT);//过期时间
            //命令浏览器保存cookie
            response.addCookie(cookie);
        }


    }
}
