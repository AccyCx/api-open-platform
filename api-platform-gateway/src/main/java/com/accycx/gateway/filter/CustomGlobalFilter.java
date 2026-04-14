package com.accycx.gateway.filter;

import com.accycx.common.service.InnerUserService;
import com.accycx.common.utils.SignUtils;
import com.accycx.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

//    远程调用主后台获取用户信息
    @DubboReference(check = false)
    @SuppressWarnings("unused")
    private InnerUserService innerUserService;

//    Mono<Void> 是 Reactor 框架中的一个类型，表示一个异步操作的结果，这个操作可能会完成（成功或失败），但不会返回任何数据（Void）。
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain){
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

//        1.打印请求日志
        log.info("请求唯一标识：{}",request.getId());

//        request.getPath() 返回的是一个 RequestPath 对象。这个对象里包含了很多解析好的路径信息，比如按 / 分割的各个部分。如果直接打印它，底层会调用 toString()。
//        request.getPath().value() 则是直接把完整的路径提取成一个纯粹的 String 字符串（比如 "/api/user/login"）。在写日志时，我们通常只需要纯字符串。
        log.info("请求路径：{}",request.getPath().value());

//        request.getLocalAddress() 返回的是一个 InetSocketAddress 对象，里面包含了 IP 地址、端口号，甚至还有未解析的主机名。
//        request.getLocalAddress().getHostString() 则是干净利落地只提取出 IP 地址字符串（比如 "192.168.1.100"），且它不会触发耗时的 DNS 反向解析，性能更好，写进日志也更清晰。
        InetSocketAddress localAddress = request.getLocalAddress();
        if(localAddress != null){
            log.info("请求来源地址：{}",localAddress.getHostString());
        } else {
            log.warn("无法获取请求来源地址");
        }

//        2.扒取请求头（核心鉴权部分）
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");
        String sign = headers.getFirst("sign");
        String body = headers.getFirst("body");

//        3.校验逻辑（防伪造、防重放、防过期）
        if(accessKey == null){
            return handleNoAuth(response);
        }

        User invokeUser = innerUserService.getInvokeUser(accessKey);
        if(invokeUser == null){
            return handleNoAuth(response);
        }
//        一般做法：把nonce存进Redis，如果发现这个nonce已经存在，说明是黑客在重放攻击，直接拒绝
//        这里先简单校验长度，大于4位即可
        if(nonce == null || nonce.length() < 4){
            return handleNoAuth(response);
        }

        long currentTime = System.currentTimeMillis() / 1000;
        final long FIVE_MINUTES = 5 * 60;
        if(timestamp == null || (currentTime - Long.parseLong(timestamp)) >= FIVE_MINUTES){
            return handleNoAuth(response);
        }

        String secretKey = invokeUser.getSecretKey();
        String serverSign = SignUtils.genSign(body, secretKey);
        if(sign == null || !sign.equals(serverSign)){
            return handleNoAuth(response);
        }

//        4.鉴权通过，放行请求
        return chain.filter(exchange);

    }

    /**
     * 拦截并返回403无权限错误
     */
    private Mono<Void> handleNoAuth(ServerHttpResponse response){
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }
    @Override
    public int getOrder() {
//        返回-1保证这个过滤器拥有最高优先级，最先执行
        return -1;
    }
}
