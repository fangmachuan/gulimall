package com.atguigu.gulimall.cart.vo;

import lombok.Data;
import lombok.ToString;

/**
 * 可以在目标方法执行之前
 */
@ToString
@Data
public class UserInfoTo {
    private Long userId;  //如果登录了就会有用户的id
    private String userKey; //临时用户身份

    private boolean tempUser = false; //如果cookie里面有临时用户就为true,否则就是false 默认是false
}
