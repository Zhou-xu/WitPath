package com.witpath.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // 配置文件 application.yml 配置：jwt.secret 和 jwt.expire
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private Long expire; // 单位：毫秒，比如 30*60*1000 = 30分钟

    /**
     * Token 有效期（毫秒），供登录响应告知前端
     */
    public Long getExpire() {
        return expire;
    }

    /**
     * 生成JWT token
     * @param userId LoginInitId，可以作为主体
     * @return jwt字符串
     */
    public String createToken(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire);

        return Jwts.builder()
                .setSubject(userId.toString()) // 主题，存用户标识
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析token，拿到userId
     */
    public Long getUserId(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 校验token是否合法、没过期
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            // token过期
        } catch (MalformedJwtException e) {
            // token格式错误
        } catch (SignatureException e) {
            // 签名错误，被篡改
        } catch (Exception e) {
        }
        return false;
    }
}
