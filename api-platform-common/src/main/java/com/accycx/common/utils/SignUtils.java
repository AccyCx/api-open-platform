package com.accycx.common.utils;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * API签名工具类
 */
public class SignUtils {
    /**
     * 生成API调用签名
     *
     * @param body 请求体内容（或者请求参数）
     * @param secretKey 用户的私钥
     * @return 经过MD5加密的签名字符串
     */
    public static String genSign(String body,String secretKey){

//        防止明文拼接被破解，可以在body和secretKey之间加入一个固定的分隔符，增加破解难度
        String content = body+ "." + secretKey;

        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
//            TODO:一般还会把随机数（Nonce）和时间戳（Timestamp）加入签名拼接中，以此防范“重放攻击，
//             目前先用最精简的 body + SK 跑通主流程，后面做网关拦截时可以再加固
    }
}
