package com.scbrbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /**
     * JWT密钥
     */
    private String secret;

    /**
     * 过期时间（小时）
     */
    private Long expireHours;
}
