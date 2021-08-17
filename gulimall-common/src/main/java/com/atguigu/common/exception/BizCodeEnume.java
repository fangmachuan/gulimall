package com.atguigu.common.exception;

public enum BizCodeEnume {
    UNKNOW_EXCEPTION(10000,"系统未知异常"),
    VATLD_EXCEPTION(10001,"参数格式校验失败"),
    ROO_MANY_REQUEST(10003,"服务出现异常,请稍后再试"),
    TOO_MANY_REQUEST(10008,"请求流量过大,请稍后再试"),
    SMS_CODE_EXCEPTION(10004,"验证码获取频率太高，请稍后再试"),
    PRODUCT_UP_EXCEPTION(11000,"商品上架异常"),
    USER_EXIST_EXCEPTION(15001,"用户已存在"),
    PHONE_EXIST_EXCEPTION(15002,"手机号已存在"),
    NO_STOCK_EXCEPTION(21000,"商品库存不足"),
    LOGINACCT_PASSWORD_INVAILD_EXCEPTION(15003,"账号密码错误");

    private Integer code;
    private String msg;
    BizCodeEnume(Integer code, String msg){
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
