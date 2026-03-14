package com.scbrbackend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类，注册相关 Handler 和拦截器
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AnalysisStreamHandler analysisStreamHandler;
    private final AnalysisStreamHandshakeInterceptor analysisStreamHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 使用 '*' 通配符来匹配动态的 path variable ({scheduleId})
        registry.addHandler(analysisStreamHandler, "/ws/v1/analysis/stream/*")
                .addInterceptors(analysisStreamHandshakeInterceptor)
                .setAllowedOrigins("*"); // 允许跨域
    }
}
