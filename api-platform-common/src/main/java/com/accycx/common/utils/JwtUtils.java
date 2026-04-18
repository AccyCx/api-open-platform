package com.accycx.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys; // 必须导入这个 Keys
import io.jsonwebtoken.ExpiredJwtException; // 用于捕获过期异常
import io.jsonwebtoken.MalformedJwtException; // 用于捕获格式异常
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {
    // Token过期时间，这里设置为7天
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;

    // JWT 签名密钥（必须满足新版 HS512 的安全长度要求）
    private static final String SECRET_KEY = "api_platform_jwt_secret_key_accycx_must_be_very_long_for_security_reasons_123456";

    // 将字符串秘钥转换成安全规范的Key对象
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    /**
     * 生成Token
     * @param userId 用户ID
     * @param userAccount 用户账号
     * @return 生成的JWT字符串，包含用户信息和过期时间
     */
    public static String generateToken(Long userId, String userAccount){
        Map<String,Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userAccount", userAccount);

        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(KEY, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 解析Token
     * @param token 客户端传来的JWT字符串
     * @return Claims 载荷对象，里面包含用户信息
     */
    public static Claims parseToken(String token) {
        try {
//            使用parserBuilder设置密钥并解析token
            return Jwts.parserBuilder()
                    .setSigningKey(KEY) //必须使用签发时的同一把钥匙，比对密钥
                    .build()//准备就绪，构造解析器
                    .parseClaimsJws(token)//解析token，如果token无效或过期会抛出异常
                    .getBody(); //获取token中的payload部分，也就是我们之前放入的claims
        } catch (ExpiredJwtException e) {
//            如果Token已经过了EXPIRE_TIME，会抛出这个异常
            throw new RuntimeException("Token已过期，请重新登录");
        } catch (MalformedJwtException e) {
//            如果Token被人篡改过，或者根本不是一个合法的JWT，会抛出这个异常
            throw new RuntimeException("Token不合法或被篡改");
        } catch (Exception e) {
//            其他异常（如签名无效等）
            throw new RuntimeException("Token解析失败");
        }
    }
}