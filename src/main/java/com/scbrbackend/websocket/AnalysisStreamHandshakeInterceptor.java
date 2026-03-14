package com.scbrbackend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手拦截器，用于鉴权与提取路径参数
 */
@Slf4j
@Component
public class AnalysisStreamHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        URI uri = request.getURI();
        String path = uri.getPath();
        String query = uri.getQuery();

        // 1. 从 HTTP Request 的 URI 中解析出路径参数 scheduleId
        // 例如路径为 /ws/v1/analysis/stream/12345，取出最后的 12345
        String scheduleId = null;
        if (path != null) {
            String[] pathSegments = path.split("/");
            scheduleId = pathSegments[pathSegments.length - 1];
        }

        // 2. 解析 Query 参数中的 token
        String token = null;
        if (query != null) {
            String[] queryParams = query.split("&");
            for (String param : queryParams) {
                if (param.startsWith("token=")) {
                    token = param.substring("token=".length());
                    break;
                }
            }
        }

        // 3. 进行模拟校验（真实业务中这里替换为 JWT 解析逻辑）
        boolean isValid = token != null && !token.trim().isEmpty();

        // 4. 校验失败处理
        if (!isValid) {
            log.warn("\n❌ [WebSocket] 握手被拒绝：Token 为空或无效！\n   请求 URI: {}", uri);
            return false;
        }

        // 5. 校验成功处理
        if (scheduleId != null && !scheduleId.trim().isEmpty()) {
            attributes.put("scheduleId", scheduleId);
            log.info("\n======================================================\n" +
                     "✅ [WebSocket] 握手授权成功！\n" +
                     "   请求地址  : {}\n" +
                     "   排课 ID   : {}\n" +
                     "   凭证 Token: {}\n" +
                     "======================================================", 
                     uri, scheduleId, token);
            return true;
        } else {
            log.warn("\n❌ [WebSocket] 握手被拒绝：未能从路径中提取到有效的 scheduleId！\n   请求 URI: {}", uri);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后的逻辑，这里不需要处理
    }
}
