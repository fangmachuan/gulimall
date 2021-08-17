package com.atguigu.gulimall.ssoclient.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@Controller
public class HelloController {

    @Value("${sso.server.url}")
    String ssoServerUrl;



    /**
     * 无需登录就可以访问
     * @return
     */
    @ResponseBody
    @GetMapping("/hello")
    public String hello(){
        return "hello";
    }

    /**
     * 登录后才能访问的
     * 感知这次是在认证服务器登录成功跳回来的
     * @param model
     * @param session
     * @param token 只要去认证服务器登录成功跳回来了，才会有token，否则没有token
     * @return
     */
    @GetMapping("/employees")
    public String employees(Model model, HttpSession session,@RequestParam(value = "token",required = false) String token){
        if (!StringUtils.isEmpty(token)){ //token不是空的，说明是认证服务器登录后，从客户端指定的地址跳回来的，就说明登录成功了
            //去认证服务器获取当前token的真正对应的用户信息
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> forEntity = restTemplate.getForEntity("http://ssoserver.com:8080/userInfo?token=" + token, String.class);
            String body = forEntity.getBody();//获取响应的内容
            session.setAttribute("loginUser",body);
        }
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser==null){ //没登录,就跳转到登录服务器进行登录
            return "redirect:"+ssoServerUrl+"?redirect_url=http://client1.com:8081/employees";//重定向
        }else { //登录了才给你展示页面
            List<String> emps = new ArrayList<>();
            emps.add("张三");
            emps.add("李四");
            model.addAttribute("emps",emps);
            return "list";
        }

    }

}
