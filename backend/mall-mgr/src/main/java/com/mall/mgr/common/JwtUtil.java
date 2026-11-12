package com.mall.mgr.common;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 令牌工具（mall-mgr 平台管理服务公共工具）
 * <p>负责签发 JWT；网关侧使用同一密钥（mall.jwt.secret）校验。令牌载荷包含
 * username、role（admin/merchant/user）、num（商户=shopNum，用户=U+账号，管理员=A+账号）。
 */
@Component
public class JwtUtil {

    @Value("${mall.jwt.secret}")
    private String secret;

    @Value("${mall.jwt.expiration:86400000}")
    private long expiration;

    /**
     * @param username 登录账号
     * @param role     admin / merchant / user
     * @param num      商户=shopNum；用户=U+账号；管理员=A+账号
     */
    public String issue(String username, String role, String num) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", Math.abs((username + role).hashCode()));
        claims.put("username", username);
        claims.put("role", role);
        claims.put("num", num == null ? "" : num);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }
}
