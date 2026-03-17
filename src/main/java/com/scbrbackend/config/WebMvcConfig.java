package com.scbrbackend.config;

import com.scbrbackend.interceptor.JwtAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/v1/**") // 拦截所有的 api 层接口
                .excludePathPatterns(
                        "/api/v1/auth/login",         // 登录接口放行
                        "/api/v1/internal/model/**", // 模型内部及回调接口放行
                        "/error"                     // spring 错误页
                );
    }
}
