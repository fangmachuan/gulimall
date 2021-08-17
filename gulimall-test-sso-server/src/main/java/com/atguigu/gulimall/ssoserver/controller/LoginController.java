package com.atguigu.gulimall.ssoserver.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Controller
public class LoginController {

    @Autowired
    StringRedisTemplate redisTemplate;

    /**
     * 返回获取登录后的用户信息
     * 根据token来查询真正对应的用户信息
     * @return
     */
    @ResponseBody
    @GetMapping("/userInfo")
    public String userInfo(@RequestParam("token") String token){
        //去redis查询token对应的信息
        String s = redisTemplate.opsForValue().get(token);
        return s;
    }

      /* 跳转到登录页
     * @return
     */
    @GetMapping("/login.html")
    public String loginPage(@RequestParam("redirect_url") String url, Model model,@CookieValue(value = "sso_token",required = false) String sso_token){
        if (!StringUtils.isEmpty(sso_token)){ //如果能获取到cookie，就说明之前已经有客户端浏览器登录过了还给浏览器留下了痕迹.那么就可以不用再登录了
            //就直接可以跳到受保护的资源页面
            return "redirect:"+url+"?token="+sso_token;
        }
        model.addAttribute("url",url);
        return "login";
    }

    @PostMapping("/doLogin")
    public String doLogin(@RequestParam("username") String username, @RequestParam("password")String password, @RequestParam("url")String url, HttpServletResponse response){
        //帐号密码不为空就说明登录成功
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)){
            //把登录成功的用户存起来，并且重定向跳转到客户端给的地址
            String uuid = UUID.randomUUID().toString().replace("-","");
            redisTemplate.opsForValue().set(uuid,username);
            //这个cookie的有效期就是当前会话，浏览器一关，那么认证服务器对应的cookie就没有了
            Cookie sso_token = new Cookie("sso_token",uuid);
            //只要有客户端登录成功就留一个cookie
            response.addCookie(sso_token);
            return "redirect:"+url+"?token="+uuid;
        }
        //登录失败还是去登录页
        return "login";
    }
}
