package com.accycx.apiplatforminterface.client;


import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.accycx.apiplatforminterface.model.User;
import com.accycx.common.utils.SignUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 调用第三方接口的客户端类
 */
public class ApiClient {

    private final String accessKey;
    private final String secretKey;

//    构造方法，强制要求调用者传入AK和SK
    public ApiClient(String accessKey,String secretKey){
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /**
     * 核心逻辑：组装请求头
     * 把凭证全部放在Header里
     */
    private Map<String,String> getHeaderMap(String body){
        Map<String,String> hashMap = new HashMap<>();
        hashMap.put("accessKey",accessKey);

//       生成随机数（防重放）
        hashMap.put("nonce", RandomUtil.randomNumbers(4));

//        生成当前时间戳（防过期）
        hashMap.put("timestamp",String.valueOf(System.currentTimeMillis() / 1000));

//        将请求体参与签名计算
        hashMap.put("body",body);

//        生成签名
        hashMap.put("sign", SignUtils.genSign(body,secretKey));

        return hashMap;
    }

//    1.调用GET接口
    public String getNameByGet(String name){
//        Hutool的HttpUtil可以简化HTTP请求的发送
        HashMap<String,Object> paramMap = new HashMap<>();
        paramMap.put("name",name);
        String result = HttpUtil.get("http://localhost:8102/name/get",paramMap);
        System.out.println(result);
        return result;
    }

//    2.调用POST URL传参接口
    public String getNameByPost(String name){
        HashMap<String,Object> paramMap = new HashMap<>();
        paramMap.put("name",name);
        String result = HttpUtil.post("http://localhost:8102/name/post",paramMap);
        System.out.println(result);
        return result;
    }

//    3.调用POST JSON接口（携带签名）
    public String getUserNameByPost(User user){
//        将User对象转为JSON字符串
        String json = JSONUtil.toJsonStr(user);

//        发送带请求头的HTTP请求
        HttpResponse httpResponse = HttpRequest.post("http://localhost:8102/name/user")
                .addHeaders(getHeaderMap(json)) //关键：把算好的签名头放进去
                .body(json) //塞入请求体
                .execute();

        System.out.println(httpResponse.body());
        String result = httpResponse.body();
        System.out.println(result);
        return result;

    }
}
