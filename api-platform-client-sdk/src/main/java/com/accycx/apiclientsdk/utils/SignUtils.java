package com.accycx.apiclientsdk.utils;

import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;

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
     * @param nonce 随机数，防止重放攻击
     * @param timestamp 时间戳，防止重放攻击
     * @return 经过MD5加密的签名字符串
     */
    public static String genSign(String body,String secretKey,String nonce, String timestamp){

//        防止明文拼接被破解，可以在body和secretKey之间加入一个固定的分隔符，增加破解难度
        String content = body+ "." + secretKey+"."+ nonce + "." + timestamp;


        Digester md5 = new Digester(DigestAlgorithm.MD5);
        return md5.digestHex(content, StandardCharsets.UTF_8);

    }

}
