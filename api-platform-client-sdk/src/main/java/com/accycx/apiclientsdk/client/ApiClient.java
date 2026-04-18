package com.accycx.apiclientsdk.client;


import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.accycx.apiclientsdk.utils.AuthUtils;
import com.accycx.apiclientsdk.model.User;
import java.util.HashMap;

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



    //    1.调用GET接口
    public String getNameByGet(String name){
//        Hutool的HttpUtil可以简化HTTP请求的发送
        HashMap<String,Object> paramMap = new HashMap<>();
        paramMap.put("name",name);
        String result = HttpUtil.get("http://localhost:8090/api/name/get",paramMap);
        System.out.println(result);
        return result;
    }

    //    2.调用POST URL传参接口
    public String getNameByPost(String name){
        HashMap<String,Object> paramMap = new HashMap<>();
        paramMap.put("name",name);
        String result = HttpUtil.post("http://localhost:8090/api/name/post",paramMap);
        System.out.println(result);
        return result;
    }

    //    3.调用POST JSON接口（携带签名）
    public String getUserNameByPost(User user){
//        将User对象转为JSON字符串
        String json = JSONUtil.toJsonStr(user);

//        发送带请求头的HTTP请求

        try (HttpResponse httpResponse = HttpRequest.post("http://localhost:8090/api/name/user")
                .addHeaders(AuthUtils.getHeaderMap(json,accessKey,secretKey))
                .body(json)
                .execute()) {

            // 只要走出了这个 try 的大括号，httpResponse 就会被自动安全关闭
            String result = httpResponse.body();
            System.out.println(result);
            return result;
        }

    }
}
