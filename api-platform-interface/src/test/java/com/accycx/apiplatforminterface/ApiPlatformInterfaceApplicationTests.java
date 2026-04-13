package com.accycx.apiplatforminterface;

import com.accycx.apiclientsdk.client.ApiClient;
import com.accycx.apiclientsdk.model.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class ApiPlatformInterfaceApplicationTests {

    // 这个没有写任何 new ApiClient() 的代码，直接注入就能用
    // 因为我们写的 SDK 里的 AutoConfiguration 已经在后台帮我们把 application.yml 里的密钥塞进去并实例化了。
    @Resource
    private ApiClient apiClient;

    @Test
    void contextLoads() {
        // 1. 准备参数
        User user = new User();
        user.setUsername("accycx");

        // 2. 一行代码，直接调用！SDK 在底层会自动算好签名、拼好请求头并发送。
        String result = apiClient.getUserNameByPost(user);

        System.out.println("测试结果：" + result);
    }
}