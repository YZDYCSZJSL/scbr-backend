package com.scbrbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scbrbackend.mapper.AnalysisDetailMapper;
import com.scbrbackend.mapper.AnalysisTaskMapper;
import com.scbrbackend.model.entity.AnalysisDetail;
import com.scbrbackend.model.entity.AnalysisTask;
import com.scbrbackend.websocket.StreamContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 核心的“三级降维落库策略”服务
 */
@Slf4j
@Service
public class AnalysisDataService {

    @Autowired
    private AnalysisDetailMapper analysisDetailMapper;

    @Autowired
    private AnalysisTaskMapper analysisTaskMapper;
    
    // 添加 CourseScheduleMapper 以获取排课信息
    @Autowired
    private com.scbrbackend.mapper.CourseScheduleMapper courseScheduleMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为实时流创建分析主表任务 (media_type = 3)
     */
    public Long createRealtimeTask(Long scheduleId) {
        // 先查询该排课关联的教师和教室
        com.scbrbackend.model.entity.CourseSchedule schedule = courseScheduleMapper.selectById(scheduleId);
        Long teacherId = (schedule != null) ? schedule.getTeacherId() : 0L;
        Long classroomId = (schedule != null) ? schedule.getClassroomId() : 0L;

        AnalysisTask task = new AnalysisTask();
        task.setTeacherId(teacherId);
        task.setClassroomId(classroomId);
        task.setScheduleId(scheduleId);
        task.setFileId(null); // 实时流没有上传 sys_file
        task.setMediaType(3); // 3-实时流
        task.setStatus(1); // 1-分析中
        task.setAttendanceCount(0);
        task.setTotalScore(new java.math.BigDecimal("100.00")); // 默认满分，后续扣减
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        analysisTaskMapper.insert(task); // MybatisPlus 会自动回填 ID
        log.info("【实时流任务建立】成功为排课 {} 生成分析任务，TaskID = {}", scheduleId, task.getId());
        return task.getId();
    }

    /**
     * 结束实时流分析任务
     */
    public void finishRealtimeTask(Long taskId) {
        if (taskId == null) return;
        AnalysisTask task = new AnalysisTask();
        task.setId(taskId);
        task.setStatus(2); // 2-成功/已结束
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
        log.info("【实时流任务结束】任务 TaskID = {} 已被标记为完成状态", taskId);
    }

    /**
     * 中频趋势落库 (record_type = 1)
     * 在定时任务中调用，用于把过去一段时间的统计数据平均化并落库
     *
     * @param context 单条实时流的上下文
     */
    public void flushTrendData(StreamContext context) {
        if (context == null) return;

        int frameTime = context.getFrameTimeSeconds();
        Long taskId = context.getTaskId();
        
        // 重置内存计数器，并获取过去 30 秒内的平均指标
        Map<String, Integer> snapshot = context.resetAndGetSnapshotAndTotalFrames();

        if (snapshot.isEmpty()) {
            return;
        }

        log.info("【中频落库】排课 {} 第 {} 秒, 趋势写入: {}", context.getScheduleId(), frameTime, snapshot);

        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            AnalysisDetail detail = new AnalysisDetail();
            detail.setTaskId(taskId);
            detail.setRecordType(1); // 1-趋势聚合
            detail.setFrameTime(frameTime);
            detail.setBehaviorType(entry.getKey());
            detail.setCount(entry.getValue());
            detail.setBoundingBoxes(null); // 趋势不存框
            detail.setSnapshotUrl(null); // 趋势不存图
            
            analysisDetailMapper.insert(detail);
        }
    }

    /**
     * 低频违规抓拍落库 (record_type = 2)
     * 发现违规后异步即时调用
     */
    @Async
    public void captureViolationAsync(StreamContext context, Iterable<Map<String, Object>> aiDetails, String base64Image) {
        if (context == null || aiDetails == null) return;
        
        int frameTime = context.getFrameTimeSeconds();
        
        // --- 核心防抖动：距离上次抓拍如果不足 10 秒，则直接跳过，防止连续违规时疯狂疯狂刷库 ---
        if (frameTime - context.getLastCaptureSecond() < 10) {
            return; 
        }

        Long taskId = context.getTaskId();

        boolean hasViolation = false;
        
        for (Map<String, Object> detailMap : aiDetails) {
            String behaviorType = (String) detailMap.get("behaviorType");
            Integer count = (Integer) detailMap.get("count");
            
            // 定义规则：达到 3 人即视为需要抓拍
            if (("玩手机".equals(behaviorType) || "趴桌".equals(behaviorType)) && count != null && count >= 3) {
                hasViolation = true;
                break;
            }
        }
        
        if (!hasViolation) {
            return;
        }

        // 更新上次抓拍时间，进入 10 秒冷却期
        context.updateLastCaptureSecond(frameTime);

        // 模拟上传图片到 OSS (实际开发中应该真正转存 Base64 然后返回 OSS URL)
        String mockOssUrl = "https://mock-oss.aliyuncs.com/snapshot_" + taskId + "_" + frameTime + ".jpg";
        log.warn("🚨🚨【低频违规抓拍】排课 {} 发现多人违规，触发抓拍! 图片URL: {}", context.getScheduleId(), mockOssUrl);

        for (Map<String, Object> detailMap : aiDetails) {
            String behaviorType = (String) detailMap.get("behaviorType");
            Integer count = (Integer) detailMap.get("count");

            if (count == null || count <= 0) continue;

            AnalysisDetail detail = new AnalysisDetail();
            detail.setTaskId(taskId);
            detail.setRecordType(2); // 2-违规抓拍
            detail.setFrameTime(frameTime);
            detail.setBehaviorType(behaviorType);
            detail.setCount(count);
            detail.setSnapshotUrl(mockOssUrl); // 落库图片 URL
            
            try {
                // 坐标数组转 JSON 字符串
                String boxesJson = objectMapper.writeValueAsString(detailMap.get("boundingBoxes"));
                detail.setBoundingBoxes(boxesJson);
            } catch (JsonProcessingException e) {
                log.error("JSON 序列化失败", e);
            }

            analysisDetailMapper.insert(detail);
        }
    }
    
    /**
     * 更新最终得分与考勤人数到主表中
     */
    public void updateTaskScore(Long taskId, int currentAttendanceCount, double totalScore) {
        if (taskId == null) return;
        
        try {
            AnalysisTask task = new AnalysisTask();
            task.setId(taskId);
            task.setAttendanceCount(currentAttendanceCount);
            // 这里用 BigDecimal 但先快速使用 SetScore
            task.setTotalScore(new java.math.BigDecimal(String.valueOf(totalScore)));
            
            analysisTaskMapper.updateById(task);
        } catch (Exception e) {
            log.error("更新任务得分失败 taskId {}: {}", taskId, e.getMessage());
        }
    }
}
