package com.accycx.apiplatforminterface;

import com.accycx.apiplatforminterface.client.ApiClient;
import com.accycx.apiplatforminterface.model.User;

public class Main {
    public static void main(String[] args) {
        String accessKey = "accycx_test_ak";
        String secretKey = "accycx_test_sk";
        ApiClient apiClient = new ApiClient(accessKey, secretKey);
        User user = new User();
        user.setUsername("accycx");
        System.out.println("----- 测试开始 -----");
        String result = apiClient.getUserNameByPost(user);
        System.out.println("服务端返回结果"+ result);
    }
}
