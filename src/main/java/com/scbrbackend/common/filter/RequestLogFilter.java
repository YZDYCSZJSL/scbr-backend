package com.scbrbackend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 全局请求日志过滤器，用于拦截并打印所有进入系统的HTTP请求和响应信息（包含 JSON Body）。
 */
@Slf4j
// @Component
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        // 1. 包装 Request 和 Response
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        String method = requestWrapper.getMethod();
        String requestURI = requestWrapper.getRequestURI();
        String queryString = requestWrapper.getQueryString();

        log.info("========== Request Start ==========");
        log.info("Request URL    : {}", requestURI + (queryString != null ? "?" + queryString : ""));
        log.info("HTTP Method    : {}", method);
        log.info("Client IP      : {}", requestWrapper.getRemoteAddr());

        // 打印普通请求参数 (针对 GET 请求或 application/x-www-form-urlencoded)
        Map<String, String[]> parameterMap = requestWrapper.getParameterMap();
        if (!parameterMap.isEmpty()) {
            StringBuilder params = new StringBuilder();
            parameterMap.forEach((key, values) -> {
                params.append(key).append("=").append(String.join(",", values)).append("; ");
            });
            log.info("Parameters     : {}", params.toString());
        }

        try {
            // 注意：这里必须传入包装后的 wrapper，否则控制器无法触发缓存机制
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long endTime = System.currentTimeMillis();

            // 2. 打印 Request Body
            String requestBody = getRequestBody(requestWrapper);
            if (requestBody != null && !requestBody.trim().isEmpty()) {
                // 如果 JSON 内容太长可能需要截断，这里直接原样输出，去掉换行符保持日志整洁
                log.info("Request Body   : {}", requestBody.replaceAll("\\r\\n|\\r|\\n", ""));
            }

            // 3. 打印 Response Body
            String responseBody = getResponseBody(responseWrapper);
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                log.info("Response Body  : {}", responseBody.replaceAll("\\r\\n|\\r|\\n", ""));
            }

            log.info("Response Status: {}", responseWrapper.getStatus());
            log.info("Time Taken     : {} ms", (endTime - startTime));
            log.info("========== Request End ==========\n");

            // 4. 重要！由于 response() 中的数据已经被缓存读取了，必须通过 copyBodyToResponse() 将其重新写回客户端
            // 否则前端将收不到任何返回数据，接口会“挂起”或返回空
            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * 从 ContentCachingRequestWrapper 中获取请求体字符串
     */
    private String getRequestBody(ContentCachingRequestWrapper requestWrapper) {
        byte[] content = requestWrapper.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return "";
    }

    /**
     * 从 ContentCachingResponseWrapper 中获取响应体字符串
     */
    private String getResponseBody(ContentCachingResponseWrapper responseWrapper) {
        byte[] content = responseWrapper.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return "";
    }
}
