package com.atguigu.gulimall.auth.controller;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.auth.feign.MemberFeignService;
import com.atguigu.gulimall.auth.feign.ThirdPartFeignService;
import com.atguigu.gulimall.auth.vo.UserLoginVo;
import com.atguigu.gulimall.auth.vo.UserRegistVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
public class LoginController {

    @Autowired
    ThirdPartFeignService thirdPartFeignService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    MemberFeignService  memberFeignService;

    /**
     * 发送验证码的
     * @param phone
     * @return
     */
    @ResponseBody
    @GetMapping("/sms/sendcode")
    public R sendCode(@RequestParam("phone") String phone){
        /**
         * 1.接口防刷,防止同一个phone在60秒内重复再次发送验证码
         * 2.验证码的再次校验，存redis,因为这个验证码不是永远存储的
         * 2.1.存redis，key是一定包含手机号的，值是验证码
         * 2.2.如果是60秒以后，那么再发新的验证码
         *
         * 验证码发送频率过多的解决方案：
         * 1.在保存验证码的时候，再保存给当前验证码设置的系统时间，
         *          1.1.只要你想要发送验证码，按照redis里面的看有没有，
         *          1.2.如果有了，再看一下这个时间，如果这个时间还在60秒以内的，那就60以后再试
         */
        //TODO 1、接口防刷。
        String rediscode = redisTemplate.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + phone);
        if (!StringUtils.isEmpty(rediscode)) { //如果从redis当中拿到的验证码不为空
            long l = Long.parseLong(rediscode.split("_")[1]);//拿到redis存的时间
            //和当前系统的时间进行比较
            if (System.currentTimeMillis() -l <60000){ //60秒内不能再发验证码
                return R.error(BizCodeEnume.SMS_CODE_EXCEPTION.getCode(),BizCodeEnume.SMS_CODE_EXCEPTION.getMsg());
            }
        }
        String code = UUID.randomUUID().toString().substring(0, 5);
        String substring = code+"_"+System.currentTimeMillis();
        redisTemplate.opsForValue().set(AuthServerConstant.SMS_CODE_CACHE_PREFIX+phone,substring,10, TimeUnit.MINUTES);
        thirdPartFeignService.sendCode(phone,code);
        return R.ok();
    }

    /**
     * 注册的控制器
     * RedirectAttributes 模拟重定向携带数据
     *  其原理：
     *      利用session原理，将数据放在session中，然后重定向到页面之后，再从session中取出来
     *      跳到下一个页面，然后取出数据以后，session里面的数据就会删掉，
     */
    @PostMapping("/regist")
    public String regist(@Valid UserRegistVo vo, BindingResult result, RedirectAttributes redirectAttributes){
        if (result.hasErrors()){ //如果校验出错
            Map<String, String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField,FieldError::getDefaultMessage));
          //存放错误消息，让前端感知并获取错误信息
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/reg.html";//注册页
        }
        //1、校验验证码，从页面提交过来的验证码
        String code = vo.getCode();
        //获取到redis存的验证码
        String s = redisTemplate.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + vo.getPhone());
        if (!StringUtils.isEmpty(s)){ //redis里面存了这个手机号对应的验证码
            //,验证码正确,就进行截串，拿到第一个数据然后进行对比
            if (code.equals(s.split("_")[0])){
                    //验证码通过以后，还必须要删除验证码,因为这样下次若还带着之前的验证码过来，就会验证失败
                    redisTemplate.delete(AuthServerConstant.SMS_CODE_CACHE_PREFIX + vo.getPhone());
                //验证码通过，  如果上面校验并无出错，就进行注册 ，调用远程服务gulimall-member进行注册
                R r = memberFeignService.regist(vo);
                if (r.getCode() == 0){ //成功就返回登陆页
                    return "redirect:http://auth.gulimall.com/login.html";
                }else { //失败就返回注册页,并且显示错误消息
                    Map<String, String> errors = new HashMap<>();
                    errors.put("msg",r.getData("msg ",new TypeReference<String>(){}));
                    redirectAttributes.addFlashAttribute("errors",errors);
                    return "redirect:http://auth.gulimall.com/reg.html";//注册页
                }
            }else { //验证不通过
                Map<String, String> errors = new HashMap<>();
                errors.put("code","验证码错误");
                //存放错误消息，让前端感知并获取错误信息
                redirectAttributes.addFlashAttribute("errors",errors);
                return "redirect:http://auth.gulimall.com/reg.html";//注册页
            }
        }else {  //redis存的验证码已过期
            Map<String, String> errors = new HashMap<>();
            errors.put("code","验证码错误");
            //存放错误消息，让前端感知并获取错误信息
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/reg.html";//注册页
        }
    }

    /**
     * 账号密码登录请求
     */
    @PostMapping("/login")
    public String login(UserLoginVo vo, RedirectAttributes redirectAttributes, HttpSession session){
        //调用远程gulimall-member进行登录
        R login = memberFeignService.login(vo);
        if (login.getCode() == 0){
            //成功登录
            //提取出用户信息,
            MemberRespVo data = login.getData("data", new TypeReference<MemberRespVo>() {
            });
            //然后将提取出来的用户信息放到session中
            session.setAttribute(AuthServerConstant.LOGIN_USER,data);
            return "redirect:http://gulimall.com";
        }else {
            Map<String,String> errors = new HashMap<>();
            errors.put("msg",login.getData("msg",new TypeReference<String>(){}));//把错误消息放到map中
            redirectAttributes.addFlashAttribute("errors",errors); //提取出错误消息
            //失败就返回登录页
            return "redirect:http://auth.gulimall.com/login.html";
        }
    }
    /**
     * 登录成功后，再点击登录页不用再登录直接跳转到首页
     */
    @GetMapping("/login.html")
    public String loginPage(HttpSession session){
        Object attribute = session.getAttribute(AuthServerConstant.LOGIN_USER);//拿到已登录的用户信息
        if (attribute==null){ //没登录，返回登录页
            return "login";
        }else { //登录了就直接跳回首页就行了
            return "redirect:http://gulimall.com";
        }

    }

}
