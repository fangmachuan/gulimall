package com.atguigu.gulimall.order.config;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gulimall.order.vo.PayVo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "alipay")
@Component
@Data
public class AlipayTemplate {

    //在支付宝创建的应用的id
    private   String app_id = "2016102100733550";

    // 商户私钥，您的PKCS8格式RSA2私钥
    public static String  merchant_private_key = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCBuvkgj5jOU/DQUFRryaPNvjiIFZHcwJE0xwOJVBMO2MjhAMSvv2PI2VYd1SevOsOX2o3leQrc32o+799x+vET8ZXXbZt0aI2wipNuYH9x4viNAlINYzGgIkCjLQmhTwFzYFDWeoLdcnaS38tF32WA+bYZn9LQAwNLxfs8g6IvCqCyIsQVuG77VvQvWdzu/R8OMuo+N8BsXHZ2/rNNXiXZWuNFPCMRGV6JFDDhoZQnEIy84ILhFOxqhvyCGnqXMLRJASo0R6h7tdOX9yqQ3HjAt+7swt68+d/AmmXWZvUqOrkOTVaa5Y+hiOYO2UpklPRknQQ0Q//RS3FqnTIYMSr9AgMBAAECggEAawD1i83llAnYj3oyp9Vhrso/hr+D2Dldi9K7MAKQ1aYpyqz/jpamj3v0dBbk7LvjqNU0RRpZw2TkFZV1EGaTgoe5uD9IAfRbKkqi4F/nvuAfcu9/DlvLCvI1rmHvl4W7BJdlFgNm/ZD0DKFa4P8qkf9mHCyfjcNubImYeIl6tKEzHMl36xvdco10su6jRyzQgsyzZijwauaKijkdMswTuey0b3DpGYejIfuutBPPwGcEYbSZUtWP2OxF5rO74b3Dxa+ZDC4h0QE0/+7D30pJ1jKR/4xyWLwvM+EVQOo5HAXmqyggX6qZKDWhIGQ5glT4nOO1LJ3xTaf9WaEko20cAQKBgQDkiI4n+jO5Pdg1JKcUCinjnCJTbBtN+ZkqEvdFlS+pQ0Cc5HSrc0Nm26iKh/t8+AffnGq34gj/RoKILVU0ZmtHzzQZJfAB0Njs00onNbv9KSdpyowbS6gmrhDU7EK0P/mF39O8U8IUkMpar4vw4s2zaIBMXJ2+S2kw9RXvg/Wp3QKBgQCRUnn0BUyFeuoe2vIGqM/wA92j4MpkL0qt8fkSgf9mi7g7aL94efQkHFuyP2tZH9jg6haRykaZm5simHdd9ReJLXGJ2841M3fA6gOkTr8ljJgsFwtzNkx+sFV3cgFtGWPt4cKhoP3a+yeZBVV8e6+PjYncGS215MigTftaWabDoQKBgQC88cnWc55gASnIhk083EpVzLj0j+TjSwG/L38RII8POnMpRBjRx7JqUCHApzzdzZN05TW2YkNeC0XQoOpZMHRyInXIdP1CAxHWe7pLQLn0TRow9S1xQ9P6Zt+zqsJblKkQbkX45qcHqc4jvA/PUTUQp82rVMd2lZKaz4cbR9bMYQKBgA75YamcVis25CLzypaXFH4V1+PICPLT0K0lztVyYb/OS9iLTZd7cYW7ClPOpyAS4QUj+dlh725qk0y4Syx6UceH0Dg1VwUyXXeaZW+r0ZRr7U41va9MfAtd3iCltbHpONvNNIH1FdNXp9fjrdBa6lcvbXIpggVJIscp4emIbV+hAoGBALRFeq6ODa5AltaYB3LX8cn+XiEOZKoGYJPxseTnB9nYi3efQ0AZqSVbIqHiqpfNcYfbkocHKnkuQmJJBXvsCRJEf362VG3C9m4ibLAgLTYBGv9hGT/ejqJAoLSAVDRJnNByaPMD8ilgxGGa4caDwAEH2ZR4vJNmzU5AgXLsgheH";

    // 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
    public static String alipay_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsgDKIMiloc+lPfCxaRp/yApjmjSf5sR9Mv7m2MUBDaS6dUu9maUFriCRfG3efU141y76JP5xGqveLnB9o6XhL0Eikl9FLHRIFcHWvk8TI10wsAio7UCOnF8HsB9xLdcON4y7RwY/WWuPG8ahrldGQAjpnTulk3aY/7mAEx+gf/aKirUEOKPHyo4J/Z1PQacvUD1XsOCjJjeupSvcHFyuVn4SJKiW6CR/RnOKRA58OmLcsvtuWyI+8bX0e3QZG9Wnt0BMraOEjI5ATPa4E+8q/ok+O3Xcm5lGpmLGJ6rlc0dPuPqgNtzmUm/IGQGZW1M0zNkM8Df301skXJh9VozbdQIDAQAB";
    // 服务器异步通知页面路径  需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    //只要支付支付成功了，会每隔几秒发送一条消息，告知支付成功了，可以接收到这个消息以后，后台修改一下订单的这个成功页
    public static String notify_url = "http://dor0fw1nn0.54http.tech/payed/notify";

    // 页面跳转同步通知页面路径 需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    //支付宝支付成功以后要跳转的页面地址
    public static String return_url = "http://member.gulimall.com/memberOrder.html";

    // 签名方式
    private  String sign_type = "RSA2";

    // 字符编码格式
    private  String charset = "utf-8";

    private String timeout = "30m";

    // 支付宝网关； https://openapi.alipaydev.com/gateway.do
    private  String gatewayUrl = "https://openapi.alipaydev.com/gateway.do";

    public  String pay(PayVo vo) throws AlipayApiException {

        //AlipayClient alipayClient = new DefaultAlipayClient(AlipayTemplate.gatewayUrl, AlipayTemplate.app_id, AlipayTemplate.merchant_private_key, "json", AlipayTemplate.charset, AlipayTemplate.alipay_public_key, AlipayTemplate.sign_type);
        //1、根据支付宝的配置生成一个支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(gatewayUrl,
                app_id, merchant_private_key, "json",
                charset, alipay_public_key, sign_type);

        //2、创建一个支付请求 //设置请求参数
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl(return_url);
        alipayRequest.setNotifyUrl(notify_url);

        //商户订单号，商户网站订单系统中唯一订单号，必填
        String out_trade_no = vo.getOut_trade_no();
        //付款金额，必填
        String total_amount = vo.getTotal_amount();
        //订单名称，必填
        String subject = vo.getSubject();
        //商品描述，可空
        String body = vo.getBody();

        alipayRequest.setBizContent("{\"out_trade_no\":\""+ out_trade_no +"\","
                + "\"total_amount\":\""+ total_amount +"\","
                + "\"subject\":\""+ subject +"\","
                + "\"body\":\""+ body +"\","
                + "\"timeout_express\":\""+timeout+"\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");

        String result = alipayClient.pageExecute(alipayRequest).getBody();

        //会收到支付宝的响应，响应的是一个页面，只要浏览器显示这个页面，就会自动来到支付宝的收银台页面
        System.out.println("支付宝的响应："+result);

        return result;

    }
}
