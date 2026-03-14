package com.scbrbackend.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记录单条课堂实时流的上下文状态，用于防并发、算帧耗时以及指标累加。
 */
@Slf4j
@Getter
public class StreamContext {

    // 关联的排课 ID
    private final String scheduleId;
    
    // 关联的 AI 分析任务 (AnalysisTask) 的 ID
    private final Long taskId;

    // 连接或开课的起始时间戳
    private final long startTimeMillis;

    // 是否正在处理的标识（背压控制保护锁）
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // 基于内存的行为累加器（BehaviorType -> 总人数累加）高频收集
    // 例如："正常听课" -> 150, "玩手机" -> 8
    private final Map<String, AtomicInteger> behaviorCounts = new ConcurrentHashMap<>();

    // 记录已经累计了多少帧（用于后续求平均值的除数基数）
    private final AtomicInteger frameAccumulatedCount = new AtomicInteger(0);

    // 记录上一次进行“违规抓拍”的相对秒数（用于低频抓拍防抖，如设置为 10 秒冷却）
    private volatile int lastCaptureSecond = -10; // 初始化为负值以确保开局即可抓拍

    public StreamContext(String scheduleId, Long taskId) {
        this.scheduleId = scheduleId;
        this.taskId = taskId;
        this.startTimeMillis = System.currentTimeMillis();
    }

    /**
     * 计算当前相对开课时间（帧秒数）
     * @return 秒数
     */
    public int getFrameTimeSeconds() {
        return (int) ((System.currentTimeMillis() - startTimeMillis) / 1000);
    }

    /**
     * 累加 AI 回传的单帧行为分析数据
     * @param aiDetails AI 识别的 details 列表 (包含 behaviorType 和 count)
     */
    public void accumulateFrameData(Iterable<Map<String, Object>> aiDetails) {
        if (aiDetails == null) return;
        
        for (Map<String, Object> detail : aiDetails) {
            String behaviorType = (String) detail.get("behaviorType");
            Integer count = (Integer) detail.get("count");
            
            if (behaviorType != null && count != null && count > 0) {
                behaviorCounts.computeIfAbsent(behaviorType, k -> new AtomicInteger(0)).addAndGet(count);
            }
        }
        frameAccumulatedCount.incrementAndGet();
    }

    /**
     * 重置所有累加器（用于 30 秒中频定时落库后调用）
     * 并返回被重置之前的快照值。
     * @return 返回快照
     */
    public Map<String, Integer> resetAndGetSnapshotAndTotalFrames() {
        Map<String, Integer> snapshot = new ConcurrentHashMap<>();
        
        // 我们需要把累加的总人数通过总帧数计算出 平均每帧 的人数
        int totalFrames = frameAccumulatedCount.getAndSet(0);
        
        for (Map.Entry<String, AtomicInteger> entry : behaviorCounts.entrySet()) {
            int totalCount = entry.getValue().getAndSet(0);
            
            // 平均人数
            int avgCount = (totalFrames == 0) ? 0 : Math.round((float) totalCount / totalFrames);
            if (avgCount > 0) {
                snapshot.put(entry.getKey(), avgCount);
            }
        }
        
        return snapshot;
    }

    /**
     * 更新最后一次抓拍的时间（用于违规防抖冷却）
     */
    public void updateLastCaptureSecond(int second) {
        this.lastCaptureSecond = second;
    }

    /**
     * 从 AI 返回详情列表中提取包含所有坐标的 JSON 字符串表示
     */
    public String extractBoundingBoxesJson(Iterable<Map<String, Object>> aiDetails, String targetBehavior, ObjectMapper objectMapper) {
        if (aiDetails == null) return null;
        for (Map<String, Object> detail : aiDetails) {
            String behaviorType = (String) detail.get("behaviorType");
            if (targetBehavior.equals(behaviorType)) {
                try {
                    return objectMapper.writeValueAsString(detail.get("boundingBoxes"));
                } catch (JsonProcessingException e) {
                    log.error("解析 BoundingBoxes JSON失败: {}", e.getMessage());
                }
            }
        }
        return null; // 这里应修正，需要导入 jackson
    }
}
