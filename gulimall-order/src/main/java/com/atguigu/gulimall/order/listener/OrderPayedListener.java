package com.atguigu.gulimall.order.listener;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gulimall.order.config.AlipayTemplate;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.PayAsyncVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 支付成功后的监听器
 */
@RestController
public class OrderPayedListener {

    @Autowired
    OrderService orderService;

    @Autowired
    AlipayTemplate alipayTemplate;

    /**
     * 订单支付成功的请求
     * @return
     */
    @PostMapping("/payed/notify")
    public String handleAlipayed(PayAsyncVo vo, HttpServletRequest request) throws AlipayApiException {
        //只要收到了支付宝给我们的异步的通知，告诉我们这个订单支付成功了，就应该我们的系统返回success给支付宝
        Map<String, String[]> map = request.getParameterMap();//接收到支付宝发来的数据参数
        for (String key : map.keySet()) { //获取到请求参数的key
            //获取到请求参数的值
            String value = request.getParameter(key);
            System.out.println("参数名："+key+"==>参数值："+value);
        }
//        //处理支付结果之前要验签名，验是不是支付宝给系统返回的数据
//        System.out.println("支付宝通知到位了,数据："+map);
//        String  result = orderService.handlePayResult(vo); //处理支付结果
//        return result;

        //验签名，看是不是支付宝给系统返回的相应数据
        Map<String,String> params = new HashMap<String,String>();
        Map<String,String[]> requestParams = request.getParameterMap();
        for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }
            //乱码解决，这段代码在出现乱码时使用
//            valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
            params.put(name, valueStr);
        }

        boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayTemplate.alipay_public_key, alipayTemplate.getCharset(), alipayTemplate.getSign_type()); //调用SDK验证签名
        if(signVerified){
            System.out.println("签名验证成功...");
            String result = orderService.handlePayResult(vo);
            return result;
        }else {
            System.out.println("签名验证失败...");
            return "error";
        }
    }
}
