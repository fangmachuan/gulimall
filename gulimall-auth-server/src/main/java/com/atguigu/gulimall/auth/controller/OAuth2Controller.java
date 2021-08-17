package com.atguigu.gulimall.auth.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.auth.feign.MemberFeignService;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.auth.vo.SocialUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理社交登录的请求控制器
 */
@Slf4j
@Controller
public class OAuth2Controller {

    @Autowired
    MemberFeignService memberFeignService;


    /**
     * 微博的成功登录后的回调方法
     * @return
     */
    @GetMapping("/oauth2.0/weibo/success")
    public String weibo(@RequestParam("code") String code ,HttpSession session) throws Exception {
//        Map<String,String> header = new HashMap<>();
//        Map<String,String> query = new HashMap<>();
        Map<String,String> header = new ConcurrentHashMap<>();
        Map<String,String> query = new ConcurrentHashMap<>();
        //换取access Token给map里面封装数据
        Map<String,String> map = new HashMap<>();//封装数据用的
        map.put("client_id","3229268135");//应用的id
        map.put("client_secret","261731da60a1fdc06753491d58f70993");
        map.put("grant_type","authorization_code");
        map.put("redirect_uri","http://auth.gulimall.com/oauth2.0/weibo/success");
        map.put("code",code);//社交登录一登录成功，code就会过来了，然后就去换取access Token
        //1.根据code码，换取一个access Token，只要能够换取access Token，就说明登录成功
        //方法参数解释：第一个参数：主机地址，，第二个参数：请求路径       第三个参数，请求方式      第四个参数 请求头   第五个参数 查询参数    第六个参数：请求体
        //执行成功这个doPost方法，就会有响应的数据
        HttpResponse response = HttpUtils.doPost("https://api.weibo.com", "/oauth2/access_token", "post", header, query, map);
        //解析响应的数据,处理access Token
       if(response.getStatusLine().getStatusCode()==200){  //获取响应状态行以及响应状态码,并且是判断是否换取成功
          //如果是200，那么就获取到了access Token
           String json = EntityUtils.toString(response.getEntity());//获取到响应体内容
           //将响应体的内容转换成对应的对象
           SocialUser socialUser = JSON.parseObject(json, SocialUser.class);
           //拿到了access Token，并且还转换了响应的对象了，那么就是知道了当前是哪个社交用户登录成功
           //真正是不是登录成功，还得分一些情况
           // 1.如果当前用户如果是第一次进gulimall网站，就自动注册进来（为当前这个社交用户生成一个会员信息账号，以后这个社交账号就对应指定的会员）
           //使用社交账号对数据库的用户表进行关联
           //如果这个社交用户，从来没有注册过gulimall网站，就进行注册，如果注册了就查出这个用户的整个详细信息
           //远程调用用户服务，接收socialUser这个社交用户，以此来判断是登录还是自动注册这个社交用户，相当于用id关联上某个本系统的用户信息，
           R oauthlogin = memberFeignService.oauthlogin(socialUser);
           if(oauthlogin.getCode() == 0){ //说明是成功的
                //提取登陆成功后的用户信息
               MemberRespVo data = oauthlogin.getData("data", new TypeReference<MemberRespVo>() {
               });
               log.info("登录成功：用户信息: {}",data.toString());
               /**
                * 第一次使用session，就命令浏览器保存相应的卡号，
                * 以后浏览器访问哪个网站就会带上这个网站的cookie
                * 子域之间：gulimall,auth.gulimall.com等
                * 发卡的时候（指定域名为父域名），即使是子域系统发的卡，也能让父域直接使用
                */
               //TODO 1.默认发的令牌。key是session,值是唯一字符串。但是作用域是当前域，那么当前域解决不了session共享的问题，解决子域session共享问题
               //TODO 2.使用JSON的序列化方式来序列化对象数据到redis中
               session.setAttribute("loginUser",data);
               //2.换取成功access Token，也即登录成功就跳到我们应用的首页
               return "redirect:http://gulimall.com";
           }else {//否则就是失败的
               return "redirect:http://auth.gulimall.com/login.html";//重定向到登录页，进行重新登录
           }
       }else {  //不成功,没有获取到access Token
           return "redirect:http://auth.gulimall.com/login.html";//重定向到登录页，进行重新登录
       }
    }

}
