package com.scbrbackend.utils;

import com.scbrbackend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        // 使用配置的secret生成密钥，如果长度不足，JJWT会抛出异常，因此要求secret尽量长(至少32字节)
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT
     *
     * @param empNo 教师工号
     * @param role  角色
     * @param name  姓名
     * @return jwt token
     */
    public String generateToken(String empNo, Integer role, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("empNo", empNo);
        claims.put("role", role);
        claims.put("name", name);

        long expMillis = System.currentTimeMillis() + (jwtProperties.getExpireHours() * 60 * 60 * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(empNo)
                .issuedAt(new Date())
                .expiration(new Date(expMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并验证 JWT
     *
     * @param token jwt token
     * @return claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 token 是否有效 (如果过期或被篡改，parseToken 时会直接抛出异常，这里仅作封装)
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getEmpNoFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject(); // subject 已经被设置为 empNo
    }

    public Integer getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", Integer.class);
    }
}
