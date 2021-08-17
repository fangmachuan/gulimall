package com.atguigu.gulimall.order.interceptor;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.vo.MemberRespVo;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginUserInterceptor implements HandlerInterceptor {

    //为了能够让其他线程都能共享到
    public static ThreadLocal<MemberRespVo> loginUser = new ThreadLocal<>();

    /**
     * 目前请求到达之前，做一个前置拦截
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //如果远程调用访问订单，订单要求远程调用的话也得先登录以后才能访问，但是这个是远程调用，无需登录的
        //解决：让拦截器所有请求一进来匹配一下，如果现在请求的路径是/order/order/status/{orderSn}这个路径，就放行
        String uri = request.getRequestURI();//当前请求 payed/notify
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        boolean match = antPathMatcher.match("/order/order/status/**", uri);//路径的匹配器
        boolean match1 = antPathMatcher.match("/payed/notify", uri);
        if (match || match1){
            return true; //直接放行
        }

        //拿到这个用户，登录了才能进行访问
        MemberRespVo attribute = (MemberRespVo) request.getSession().getAttribute(AuthServerConstant.LOGIN_USER);//获取这个登录用户
        if (attribute!=null){ //如果能获取到就说明登录了，就放行，放行要能够让其他人都访问到当前的用户信息，就可以取出这个登录的用户
            loginUser.set(attribute); //只要登录了就放进去，这样以后其他线程就都能获取到
            return true;
        }else { //如果没有登录，就提示它去登录
            request.getSession().setAttribute("msg","请先进行登录");
            response.sendRedirect("http://auth.gulimall.com/login.html");//重定向到登录页
            return false;
        }
    }
}
