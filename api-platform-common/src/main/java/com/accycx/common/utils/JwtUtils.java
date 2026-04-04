package com.accycx.common.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys; // 必须导入这个 Keys

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
}