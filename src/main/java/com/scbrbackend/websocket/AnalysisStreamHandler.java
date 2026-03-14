package com.scbrbackend.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scbrbackend.service.AnalysisDataService;
import com.scbrbackend.service.MockPythonAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 核心消息处理器，主要处理连接会话管理和心跳响应
 */
@Slf4j
@Component
public class AnalysisStreamHandler extends TextWebSocketHandler {

    // 存储所有的 WebSocketSession，按 scheduleId 分组管理
    // 外层 Key: scheduleId，内层 Key: sessionId
    private static final Map<String, Map<String, WebSocketSession>> SESSION_MAP = new ConcurrentHashMap<>();

    // 线程安全的计数器，记录当前总连接数
    private static final AtomicInteger TOTAL_CONNECTIONS = new AtomicInteger(0);

    // 缓存当前的上下文
    private static final Map<String, StreamContext> CONTEXT_MAP = new ConcurrentHashMap<>();

    // Jackson JSON 处理器
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockPythonAiService mockPythonAiService;

    @Autowired
    private AnalysisDataService analysisDataService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从 attributes 中取出拦截器放入的 scheduleId
        String scheduleId = (String) session.getAttributes().get("scheduleId");
        String sessionId = session.getId();

        // 将 session 存入分组内
        SESSION_MAP.computeIfAbsent(scheduleId, k -> new ConcurrentHashMap<>()).put(sessionId, session);
        int currentCount = TOTAL_CONNECTIONS.incrementAndGet();

        // 为该 scheduleId 初始化一个流式上下文
        // 调用 Service 创建一条真正的 analysis_task 记录（标记为实时流 media_type=3）
        Long realTaskId = analysisDataService.createRealtimeTask(Long.parseLong(scheduleId));

        CONTEXT_MAP.computeIfAbsent(scheduleId, k -> {
            return new StreamContext(scheduleId, realTaskId);
        });

        log.info("\n======================================================\n" +
                 "📡 [WebSocket] 通道连接已完全建立，准备接收视频帧！\n" +
                 "   排课 ID     : {}\n" +
                 "   Session ID  : {}\n" +
                 "   当前总连接数: {}\n" +
                 "======================================================", 
                 scheduleId, sessionId, currentCount);

        // 立即向前端发送一条初始化识别结果（全 0 或初始值），证明通路不仅连通且能传数据
        String initMessage = "{\"type\": \"result\", \"attendanceCount\": 0, \"totalScore\": 0.0, \"details\": []}";
        session.sendMessage(new TextMessage(initMessage));
        log.info("已向前端推送初始数据包 (SessionID: {})", sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String scheduleId = (String) session.getAttributes().get("scheduleId");
        String sessionId = session.getId();

        // 从 Map 中安全移除 session
        Map<String, WebSocketSession> groupSessions = SESSION_MAP.get(scheduleId);
        if (groupSessions != null) {
            groupSessions.remove(sessionId);
            // 如果该分组下已经没有连接，清理掉整个 scheduleId key 以防内存泄漏
            if (groupSessions.isEmpty()) {
                SESSION_MAP.remove(scheduleId);
                StreamContext removedContext = CONTEXT_MAP.remove(scheduleId);
                // 落地最后一波残留数据
                if (removedContext != null) {
                   analysisDataService.flushTrendData(removedContext);
                   // 实时流结束，更新 task 状态为已完成
                   analysisDataService.finishRealtimeTask(removedContext.getTaskId());
                }
            }
        }
        TOTAL_CONNECTIONS.decrementAndGet();

        log.info("\n======================================================\n" +
                 "🔌 [WebSocket] 通道连接已断开！\n" +
                 "   排课 ID     : {}\n" +
                 "   Session ID  : {}\n" +
                 "======================================================", 
                 scheduleId, sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        try {
            // 解析前端发来的 JSON 字符串
            JsonNode jsonNode = objectMapper.readTree(payload);

            // 如果是心跳信号
            if (jsonNode.has("type") && "ping".equals(jsonNode.get("type").asText())) {
                // 提取前端的 timestamp (如果没有传则使用当前后端时间作为保护机制)
                long timestamp = jsonNode.has("timestamp") ? jsonNode.get("timestamp").asLong() : System.currentTimeMillis();

                // 立即构建对应的 pong JSON
                String pongJson = String.format("{\"type\": \"pong\", \"timestamp\": %d}", timestamp);
                session.sendMessage(new TextMessage(pongJson));

                log.info("收到心跳 Ping，已回复 Pong (SessionID: {})", session.getId());
            } else if (jsonNode.has("type") && "frame".equals(jsonNode.get("type").asText())) {
                String scheduleId = (String) session.getAttributes().get("scheduleId");
                log.info("收到视频帧数据 (scheduleId: {}), 准备调用 Mock AI 处理...", scheduleId);
                StreamContext context = CONTEXT_MAP.get(scheduleId);

                if (context != null) {
                    // 背压控制：如果正在处理上一帧，直接丢弃新帧，以防 OOM 或线程堆积卡死
                    if (context.getIsProcessing().compareAndSet(false, true)) {
                        String imageBase64 = jsonNode.get("image").asText();
                        
                        try {
                            // 调用 Mock AI
                            Map<String, Object> aiResult = mockPythonAiService.predict(imageBase64);
                            
                            // 高频收集至内存累加器
                            @SuppressWarnings("unchecked")
                            Iterable<Map<String, Object>> details = (Iterable<Map<String, Object>>) aiResult.get("details");
                            context.accumulateFrameData(details);
                            
                            // 判断是否触发低频违规抓拍
                            analysisDataService.captureViolationAsync(context, details, imageBase64);
                            
                            // 发送结果回前端
                            ObjectNode resultNode = objectMapper.valueToTree(aiResult);
                            resultNode.put("type", "result"); // 契约约定的包装
                            String resultMessage = objectMapper.writeValueAsString(resultNode);
                            
                            // 推送给同一 scheduleId 下的所有前端客户端
                            broadcastToSchedule(scheduleId, resultMessage);
                            
                        } catch (Exception processEx) {
                           log.error("AI 预测发生异常", processEx);
                        } finally {
                            // 必须释放处理锁，允许接收下一帧
                            context.getIsProcessing().set(false);
                        }
                    } else {
                        log.warn("【背压抛弃】前端推送帧太快，排课 {} 的 AI 还在处理上一帧！丢弃该帧...", scheduleId);
                    }
                }
            }

        } catch (Exception e) {
            log.error("WebSocket Message解析异常: {}", e.getMessage());
        }
    }

    /**
     * 将消息广播给指定排课组的所有 Session
     */
    private void broadcastToSchedule(String scheduleId, String message) {
        Map<String, WebSocketSession> groupSessions = SESSION_MAP.get(scheduleId);
        if (groupSessions != null) {
            TextMessage textMessage = new TextMessage(message);
            groupSessions.forEach((id, session) -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("广播消息异常, SessionID: {}", id, e);
                }
            });
        }
    }

    /**
     * 定时中频落库 (每 30 秒执行一次)
     * 遍历所有目前活跃直播的 StreamContext 缓存，结算趋势指标写入 DB。
     */
    @Scheduled(fixedRate = 30000)
    public void scheduledFlushTrendData() {
        if (!CONTEXT_MAP.isEmpty()) {
            log.info("--- 定时任务：开始结算当前 {} 个课堂的 30 秒趋势统计 ---", CONTEXT_MAP.size());
            for (StreamContext context : CONTEXT_MAP.values()) {
                analysisDataService.flushTrendData(context);
            }
        }
    }
}
