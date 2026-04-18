package com.accycx.apiclientsdk.utils;

import java.util.HashMap;
import java.util.Map;

public class AuthUtils {
    /**
     * 核心逻辑：组装请求头
     * 把凭证全部放在Header里
     */
    public static Map<String,String> getHeaderMap(String body, String accessKey, String secretKey){
        Map<String,String> hashMap = new HashMap<>();
        hashMap.put("accessKey",accessKey);

//       生成随机数（防重放）
        hashMap.put("nonce", cn.hutool.core.util.RandomUtil.randomNumbers(4));

//        生成当前时间戳（防过期）
        hashMap.put("timestamp",String.valueOf(System.currentTimeMillis() / 1000));

//        将请求体参与签名计算
        hashMap.put("body",body);

//        生成签名
        hashMap.put("sign", SignUtils.genSign(body,secretKey,hashMap.get("nonce"),hashMap.get("timestamp")));

        return hashMap;
    }
}
