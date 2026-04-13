package com.accycx.apiplatforminterface.interceptor;


import com.accycx.common.service.InnerUserService;
import com.accycx.common.utils.SignUtils;
import com.accycx.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 调用全局权限拦截器
 */
@Component
public class ApiAuthInterceptor implements HandlerInterceptor {

    @SuppressWarnings("unused")
    @DubboReference //核心：告诉Dubbo这是一个服务引用，远程调用 InnerUserService 的方法
    private InnerUserService innerUserService;


    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
//        1.从请求头中扒取调用方带过来的凭证
        String accessKey = request.getHeader("accessKey");
        String nonce = request.getHeader("nonce");
        String timestamp = request.getHeader("timestamp");
        String sign = request.getHeader("sign");
        String body = request.getHeader("body");


//        2.校验AK是否存在及合法
        if(accessKey == null ){
            throw new RuntimeException("无权限：AccessKey 不存在");
        }

        User invokeUser = innerUserService.getInvokeUser(accessKey);
        if(invokeUser == null){
            throw new RuntimeException("无权限：AccessKey 错误");
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
        String secretKey = invokeUser.getSecretKey();
        String serverSign = SignUtils.genSign(body,secretKey);

//        比对调用方传来的签名和算出来的签名是否一致
        if(sign == null || !sign.equals(serverSign)){
            throw new RuntimeException("无权限：签名校验失败，数据可能被篡改！");
        }

//        所有安检通过，放行请求，进入对应的Controller
        return true;
    }
}
