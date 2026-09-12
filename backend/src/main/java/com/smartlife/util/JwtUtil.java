package com.smartlife.util;

import com.smartlife.common.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：HS256 签名，携带 userId/role/nickname
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMs;
    private final long refreshThresholdMs;

    public JwtUtil(@Value("${smartlife.jwt.secret}") String secret,
                   @Value("${smartlife.jwt.expire-minutes}") long expireMinutes,
                   @Value("${smartlife.jwt.refresh-threshold-minutes}") long refreshMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireMinutes * 60 * 1000;
        this.refreshThresholdMs = refreshMinutes * 60 * 1000;
    }

    public String createToken(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("role", user.getRole())
                .claim("nick", user.getNickname())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析并校验 token，非法/过期返回 null（交由拦截器返回 401）
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("role", Integer.class),
                    claims.get("nick", String.class));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 剩余有效期是否已低于刷新阈值（滑动续期）
     */
    public boolean needRefresh(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
            long remain = claims.getExpiration().getTime() - System.currentTimeMillis();
            return remain < refreshThresholdMs;
        } catch (Exception e) {
            return false;
        }
    }
}
