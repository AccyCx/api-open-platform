package com.accycx.apiplatforminterface.interceptor;


import com.accycx.common.utils.SignUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 调用全局权限拦截器
 */
@Component
public class ApiAuthInterceptor implements HandlerInterceptor {

//    模拟数据库中查出来的分配给这个用户的真实AK和SK
    private static final String MOCK_AK = "accycx_test_ak";
    private static final String MOCK_SK = "accycx_test_sk";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception{
//        1.从请求头中扒取调用方带过来的凭证
        String accessKey = request.getHeader("accessKey");
        String nonce = request.getHeader("nonce");
        String timestamp = request.getHeader("timestamp");
        String sign = request.getHeader("sign");
        String body = request.getHeader("body");

//        2.校验AK是否存在及合法
        if(accessKey == null || !accessKey.equals(MOCK_AK)){
            throw new RuntimeException("无权限：AccessKey 错误或不存在");
        }

//        3.防重放：校验随机数（简单版）
//        一般做法：把nonce存进Redis，如果发现这个nonce已经存在，说明是黑客在重放攻击，直接拒绝
//        这里先简单校验长度，大于4位即可
        if(nonce == null || nonce.length() <4){
            throw new RuntimeException("无权限：非法请求（Nonce 不合法）");
        }

//        4.防过期：校验时间戳
        if(timestamp == null){
            throw new RuntimeException("无权限：缺少时间戳");
        }

//        计算当前时间与请求时间的差值（设定请求有效期为5分钟）
        long currentTime = System.currentTimeMillis() / 1000;
        final long FIVE_MINUTES = 5 * 60;
        if((currentTime - Long.parseLong(timestamp)) >= FIVE_MINUTES){
            throw new RuntimeException("无权限：请求过期");
        }

//        5.校验签名
//        服务端使用同样的body和查出来的真实SK再算一遍签名
        String serverSign = SignUtils.genSign(body,MOCK_SK);

//        比对调用方传来的签名和算出来的签名是否一致
        if(sign == null || !sign.equals(serverSign)){
            throw new RuntimeException("无权限：签名校验失败，数据可能被篡改！");
        }

//        所有安检通过，放行请求，进入对应的Controller
        return true;
    }
}
