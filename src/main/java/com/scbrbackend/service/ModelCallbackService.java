package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.AnalysisDetailMapper;
import com.scbrbackend.mapper.AnalysisTaskMapper;
import com.scbrbackend.mapper.SysWeightConfigMapper;
import com.scbrbackend.model.dto.BehaviorWeightItem;
import com.scbrbackend.model.dto.ModelFailCallbackDTO;
import com.scbrbackend.model.dto.ModelFileCallbackDTO;
import com.scbrbackend.model.entity.AnalysisDetail;
import com.scbrbackend.model.entity.AnalysisTask;
import com.scbrbackend.model.entity.SysWeightConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.scbrbackend.mapper.CourseScheduleMapper;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.mapper.AnalysisTaskLogMapper;
import com.scbrbackend.model.entity.AnalysisTaskLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCallbackService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisDetailMapper analysisDetailMapper;
    private final SysWeightConfigMapper sysWeightConfigMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    @Autowired
    private AnalysisTaskLogMapper analysisTaskLogMapper;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private ReportService reportService;

    @Transactional
    public void handleSuccess(ModelFileCallbackDTO callbackDTO) {
        Long taskId = callbackDTO.getTaskId();
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("回调任务不存在: {}", taskId);
            return;
        }

        if (task.getStatus() != 1) { // 必须为 分析中
            log.warn("任务当前状态不是分析中, taskId: {}, status: {}", taskId, task.getStatus());
            return;
        }

        // 保存详情
        List<ModelFileCallbackDTO.Detail> details = normalizeDetails(callbackDTO.getDetails());
        if (details != null && !details.isEmpty()) {
            for (ModelFileCallbackDTO.Detail detailDto : details) {
                AnalysisDetail detail = new AnalysisDetail();
                detail.setTaskId(taskId);
                detail.setRecordType(detailDto.getRecordType() != null ? detailDto.getRecordType() : 0);
                detail.setSnapshotUrl(detailDto.getSnapshotUrl());
                detail.setFrameTime(detailDto.getFrameTime() != null ? detailDto.getFrameTime() : 0);
                detail.setBehaviorType(detailDto.getBehaviorType());
                detail.setCount(detailDto.getCount() != null ? detailDto.getCount() : 0);

                if (detailDto.getBoundingBoxes() != null) {
                    try {
                        detail.setBoundingBoxes(objectMapper.writeValueAsString(detailDto.getBoundingBoxes()));
                    } catch (JsonProcessingException e) {
                        log.error("JSON 序列化失败", e);
                    }
                }

                analysisDetailMapper.insert(detail);
            }
        }

        // 先写 attendanceCount，再计算总分（总分 = 行为得分 - 出勤扣分）
        task.setAttendanceCount(callbackDTO.getAttendanceCount() != null ? callbackDTO.getAttendanceCount() : 0);

        double totalScore = calculateScore(
                task.getScheduleId(),
                task.getAttendanceCount(),
                details
        );

        task.setStatus(2);
        task.setFinishTime(LocalDateTime.now());
        task.setTotalScore(new BigDecimal(String.valueOf(totalScore)));
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);

        AnalysisTaskLog finLog = new AnalysisTaskLog();
        finLog.setTaskId(taskId);
        finLog.setStage("FINISHED");
        finLog.setStatus(1);
        finLog.setMessage("任务执行成功并完成结果回写");
        try {
            Map<String, Object> finDetail = new HashMap<>();
            finDetail.put("attendanceCount", task.getAttendanceCount());
            finDetail.put("totalScore", totalScore);
            finLog.setDetailJson(objectMapper.writeValueAsString(finDetail));
        } catch (Exception e) {}
        finLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(finLog);

        // 自动生成 report
        try {
            reportService.generateReport(taskId, null);
            log.info("内部自动生成报告成功, taskId: {}", taskId);
        } catch (Exception e) {
            log.error("内部自动生成报告失败, taskId: {}", taskId, e);
            AnalysisTaskLog errLog = new AnalysisTaskLog();
            errLog.setTaskId(taskId);
            errLog.setStage("REPORT_GENERATION");
            errLog.setStatus(0);
            errLog.setMessage("生成报告失败: " + e.getMessage());
            errLog.setCreatedAt(LocalDateTime.now());
            analysisTaskLogMapper.insert(errLog);
        }
    }

    private List<ModelFileCallbackDTO.Detail> normalizeDetails(List<ModelFileCallbackDTO.Detail> details) {
        if (details == null || details.isEmpty()) {
            return details;
        }

        for (ModelFileCallbackDTO.Detail detail : details) {
            if (detail == null) {
                continue;
            }

            // boundingBoxes 为空时，保持原 count
            if (detail.getBoundingBoxes() == null) {
                continue;
            }

            int boxCount = detail.getBoundingBoxes().size();
            Integer rawCount = detail.getCount() == null ? 0 : detail.getCount();

            if (boxCount > 0 && rawCount != boxCount) {
                log.warn("检测到 count 与 boundingBoxes 数量不一致，已按 boundingBoxes.size() 修正。behaviorType={}, frameTime={}, rawCount={}, boxCount={}",
                        detail.getBehaviorType(),
                        detail.getFrameTime(),
                        rawCount,
                        boxCount);

                detail.setCount(boxCount);
            }
        }

        return details;
    }


    @Transactional
    public void handleFail(ModelFailCallbackDTO callbackDTO) {
        Long taskId = callbackDTO.getTaskId();
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(3); // 3-失败
            task.setFinishTime(LocalDateTime.now()); // 补充
            String errReason = callbackDTO.getErrorMessage();
            if (errReason == null || errReason.isEmpty()) {
                errReason = "模型服务回调通知失败";
            }
            
            String simplifiedReason = AnalysisTaskService.simplifyErrorMessage(errReason);
            task.setFailReason(simplifiedReason); // 补充
            task.setUpdatedAt(LocalDateTime.now());
            analysisTaskMapper.updateById(task);

            AnalysisTaskLog failLog = new AnalysisTaskLog();
            failLog.setTaskId(taskId);
            failLog.setStage("FAILED");
            failLog.setStatus(0);
            failLog.setMessage(simplifiedReason);
            
            try {
                Map<String, Object> detailMap = new HashMap<>();
                detailMap.put("originalError", errReason);
                failLog.setDetailJson(objectMapper.writeValueAsString(detailMap));
            } catch (Exception e) {
                log.warn("序列化原始错误失败: {}", e.getMessage());
                // 降级容错
                failLog.setDetailJson("{\"originalError\":\"序列化失败\"}");
            }
            
            failLog.setCreatedAt(LocalDateTime.now());
            analysisTaskLogMapper.insert(failLog);
        }
    }

    public double calculateScore(Long scheduleId,
                                  Integer attendanceCount,
                                  List<ModelFileCallbackDTO.Detail> details) {
        if (scheduleId == null) return 0.0;
        CourseSchedule schedule = courseScheduleMapper.selectById(scheduleId);
        int studentCount = (schedule != null && schedule.getStudentCount() != null) ? schedule.getStudentCount() : 0;
        int aCount = attendanceCount == null ? 0 : attendanceCount;

        double attendanceScoreVal = 100.0;
        if (studentCount > 0) {
            attendanceScoreVal = Math.min(100.0, (double) aCount / studentCount * 100.0);
        }

        SysWeightConfig activeConfig = sysWeightConfigMapper.selectOne(
                new LambdaQueryWrapper<SysWeightConfig>().eq(SysWeightConfig::getIsActive, 1));
        Map<String, Double> weightMap = activeConfig != null ? parseWeightConfig(activeConfig.getConfigContent()) : new HashMap<>();

        Set<Integer> uniqueFrames = new HashSet<>();
        Map<Integer, Map<String, Integer>> frameSumMap = new HashMap<>();

        if (details != null) {
            for (ModelFileCallbackDTO.Detail d : details) {
                if (d == null || d.getBehaviorType() == null) continue;
                int ft = d.getFrameTime() == null ? 0 : d.getFrameTime();
                uniqueFrames.add(ft);
                frameSumMap.putIfAbsent(ft, new HashMap<>());
                Map<String, Integer> bMap = frameSumMap.get(ft);
                bMap.put(d.getBehaviorType(), bMap.getOrDefault(d.getBehaviorType(), 0) + (d.getCount() == null ? 0 : d.getCount()));
            }
        }

        Map<String, Double> avgCounts = new HashMap<>();
        int frameCount = uniqueFrames.isEmpty() ? 1 : uniqueFrames.size();
        for (Map<String, Integer> bMap : frameSumMap.values()) {
            for (Map.Entry<String, Integer> e : bMap.entrySet()) {
                avgCounts.put(e.getKey(), avgCounts.getOrDefault(e.getKey(), 0.0) + e.getValue());
            }
        }
        for (Map.Entry<String, Double> e : avgCounts.entrySet()) {
            avgCounts.put(e.getKey(), e.getValue() / frameCount);
        }

        double wFocus1 = weightMap.getOrDefault("正常听课", 0.0);
        double wFocus2 = weightMap.getOrDefault("阅读", 0.0);
        double wFocus3 = weightMap.getOrDefault("书写", 0.0);
        double wFocus4 = weightMap.getOrDefault("玩手机", 0.0);
        double wFocus5 = weightMap.getOrDefault("趴桌", 0.0);
        boolean focusAllZero = (wFocus1 == 0 && wFocus2 == 0 && wFocus3 == 0 && wFocus4 == 0 && wFocus5 == 0);
        double focusImpact = avgCounts.getOrDefault("正常听课", 0.0) * wFocus1 +
                avgCounts.getOrDefault("阅读", 0.0) * wFocus2 +
                avgCounts.getOrDefault("书写", 0.0) * wFocus3 +
                avgCounts.getOrDefault("玩手机", 0.0) * wFocus4 +
                avgCounts.getOrDefault("趴桌", 0.0) * wFocus5;
        double focusScoreVal = focusAllZero ? 100.0 : clampScore(100.0 + focusImpact);

        double wInt1 = weightMap.getOrDefault("举手回答问题", weightMap.getOrDefault("举手", 0.0));
        double wInt2 = weightMap.getOrDefault("起立回答问题", weightMap.getOrDefault("起立", 0.0));
        boolean intAllZero = (wInt1 == 0 && wInt2 == 0);
        double intImpact = avgCounts.getOrDefault("举手回答问题", avgCounts.getOrDefault("举手", 0.0)) * wInt1 +
                avgCounts.getOrDefault("起立回答问题", avgCounts.getOrDefault("起立", 0.0)) * wInt2;
        double interactionScoreVal = intAllZero ? 100.0 : clampScore(0.0 + intImpact);

        boolean discAllZero = (wFocus4 == 0 && wFocus5 == 0);
        double discImpact = avgCounts.getOrDefault("玩手机", 0.0) * wFocus4 +
                avgCounts.getOrDefault("趴桌", 0.0) * wFocus5;
        double disciplineScoreVal = discAllZero ? 100.0 : clampScore(100.0 + discImpact);

        double totalScoreVal = 0.25 * attendanceScoreVal +
                0.35 * focusScoreVal +
                0.15 * interactionScoreVal +
                0.25 * disciplineScoreVal;

        return clampScore(totalScoreVal);
    }

    /**
     * 解析权重配置
     */
    private Map<String, Double> parseWeightConfig(String configContent) {
        Map<String, Double> weightMap = new HashMap<>();

        try {
            JsonNode root = objectMapper.readTree(configContent);

            // 新格式：BehaviorWeightItem[]
            if (root.isArray()) {
                List<BehaviorWeightItem> items = objectMapper.readValue(
                        configContent,
                        new TypeReference<List<BehaviorWeightItem>>() {
                        });

                for (BehaviorWeightItem item : items) {
                    if (item == null) continue;

                    if (item.getBehaviorType() != null && !item.getBehaviorType().trim().isEmpty()) {
                        weightMap.put(item.getBehaviorType().trim(), item.getWeight());
                    }

                    // 兼容旧逻辑：name 也映射一次
                    if (item.getName() != null && !item.getName().trim().isEmpty()) {
                        weightMap.put(item.getName().trim(), item.getWeight());
                    }
                }
            }
            // 老格式：Map<String, Double>
            else if (root.isObject()) {
                Map<String, Double> oldMap = objectMapper.readValue(
                        configContent,
                        new TypeReference<Map<String, Double>>() {
                        });
                weightMap.putAll(oldMap);
            }

        } catch (Exception e) {
            log.warn("无法解析计分配置, 将使用空权重配置", e);
        }

        return weightMap;
    }

    /**
     * 最终裁剪到 0~100
     */
    private double clampScore(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }
}
