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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
                detail.setRecordType(0); // 0-全量明细(文件流)
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
        task.setTotalScore(new BigDecimal(String.valueOf(totalScore)));
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
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
            task.setUpdatedAt(LocalDateTime.now());
            analysisTaskMapper.updateById(task);
        }
    }

    private double calculateScore(Long scheduleId,
                                  Integer attendanceCount,
                                  List<ModelFileCallbackDTO.Detail> details) {
        double baseScore = 100.0;

        if (details == null || details.isEmpty()) {
            // 即使没有行为明细，也仍然允许按出勤情况扣分
            double attendancePenalty = calculateAttendancePenalty(scheduleId, attendanceCount);
            return clampScore(baseScore - attendancePenalty);
        }

        // 1. 获取激活权重配置
        SysWeightConfig activeConfig = sysWeightConfigMapper.selectOne(
                new LambdaQueryWrapper<SysWeightConfig>().eq(SysWeightConfig::getIsActive, 1));

        if (activeConfig == null || activeConfig.getConfigContent() == null) {
            log.warn("未找到激活的评分配置，行为得分使用默认满分 100.0");
            double attendancePenalty = calculateAttendancePenalty(scheduleId, attendanceCount);
            return clampScore(baseScore - attendancePenalty);
        }

        // 2. 解析权重配置
        Map<String, Double> weightMap = parseWeightConfig(activeConfig.getConfigContent());
        if (weightMap.isEmpty()) {
            log.warn("评分配置为空或解析失败，行为得分使用默认满分 100.0");
            double attendancePenalty = calculateAttendancePenalty(scheduleId, attendanceCount);
            return clampScore(baseScore - attendancePenalty);
        }

        // 3. 校验是否为固定7类行为
        if (!isFixedSevenBehaviorConfig(weightMap)) {
            log.warn("当前激活的评分配置不是固定7类行为配置，config={}", activeConfig.getConfigContent());
            // 这里仍然继续算，避免线上直接不可用
        }

        // 4. 先算行为得分
        Set<Integer> frameTimes = details.stream()
                .map(d -> d.getFrameTime() == null ? 0 : d.getFrameTime())
                .collect(Collectors.toSet());

        double behaviorScore;
        if (frameTimes.size() <= 1) {
            // 图片 / 单帧
            behaviorScore = calculateImageScore(details, weightMap, baseScore);
        } else {
            // 视频
            behaviorScore = calculateVideoScore(details, weightMap, baseScore);
        }

        // 5. 再算出勤扣分
        double attendancePenalty = calculateAttendancePenalty(scheduleId, attendanceCount);

        // 6. 最终总分 = 行为得分 - 出勤扣分
        double finalScore = behaviorScore - attendancePenalty;

        log.info("总分计算完成：scheduleId={}, attendanceCount={}, behaviorScore={}, attendancePenalty={}, finalScore={}",
                scheduleId, attendanceCount, behaviorScore, attendancePenalty, finalScore);

        return clampScore(finalScore);
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
     * 校验是否为固定7类
     */
    private boolean isFixedSevenBehaviorConfig(Map<String, Double> weightMap) {
        Set<String> fixedTypes = new HashSet<>();
        fixedTypes.add("举手回答问题");
        fixedTypes.add("阅读");
        fixedTypes.add("趴桌");
        fixedTypes.add("起立回答问题");
        fixedTypes.add("玩手机");
        fixedTypes.add("书写");
        fixedTypes.add("正常听课");

        // 只看固定行为是否齐全
        return weightMap.keySet().containsAll(fixedTypes);
    }

    /**
     * 图片/单帧评分
     */
    private double calculateImageScore(List<ModelFileCallbackDTO.Detail> details,
                                       Map<String, Double> weightMap,
                                       double baseScore) {
        double totalScore = baseScore;

        for (ModelFileCallbackDTO.Detail d : details) {
            if (d == null || d.getBehaviorType() == null || d.getCount() == null) {
                continue;
            }

            Double weight = weightMap.get(d.getBehaviorType());
            if (weight != null) {
                totalScore += weight * d.getCount();
            }
        }

        log.info("图片/单帧评分完成，baseScore={}, finalScore={}", baseScore, totalScore);
        return totalScore;
    }

    /**
     * 视频评分：按行为平均人数
     */
    private double calculateVideoScore(List<ModelFileCallbackDTO.Detail> details,
                                       Map<String, Double> weightMap,
                                       double baseScore) {
        double totalScore = baseScore;

        // behaviorType -> counts
        Map<String, List<Integer>> groupedCounts = new HashMap<>();

        for (ModelFileCallbackDTO.Detail d : details) {
            if (d == null || d.getBehaviorType() == null || d.getCount() == null) {
                continue;
            }

            groupedCounts
                    .computeIfAbsent(d.getBehaviorType(), k -> new ArrayList<>())
                    .add(d.getCount());
        }

        for (Map.Entry<String, List<Integer>> entry : groupedCounts.entrySet()) {
            String behaviorType = entry.getKey();
            List<Integer> counts = entry.getValue();

            Double weight = weightMap.get(behaviorType);
            if (weight == null || counts == null || counts.isEmpty()) {
                continue;
            }

            double avgCount = counts.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);

            totalScore += weight * avgCount;

            log.info("视频评分项：behaviorType={}, weight={}, avgCount={}, partialScore={}",
                    behaviorType, weight, avgCount, weight * avgCount);
        }

        log.info("视频评分完成，baseScore={}, finalScore={}", baseScore, totalScore);
        return totalScore;
    }

    /**
     * 出勤扣分：后端固定规则，不通过前端配置
     *
     * 扣分规则：
     * - 缺勤率 <= 5%   : 扣 0 分
     * - 5% < 缺勤率 <= 10%  : 扣 2 分
     * - 10% < 缺勤率 <= 20% : 扣 5 分
     * - 20% < 缺勤率 <= 30% : 扣 10 分
     * - 缺勤率 > 30%  : 扣 20 分
     */
    private double calculateAttendancePenalty(Long scheduleId, Integer attendanceCount) {
        if (scheduleId == null || attendanceCount == null) {
            log.warn("无法计算出勤扣分，scheduleId 或 attendanceCount 为空，attendancePenalty=0");
            return 0.0;
        }

        CourseSchedule schedule = courseScheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getStudentCount() == null || schedule.getStudentCount() <= 0) {
            log.warn("无法计算出勤扣分，未找到有效的应到人数，scheduleId={}, attendancePenalty=0", scheduleId);
            return 0.0;
        }

        int expectedCount = schedule.getStudentCount();
        int actualCount = Math.max(attendanceCount, 0);

        int absentCount = Math.max(expectedCount - actualCount, 0);
        double absenceRate = (double) absentCount / expectedCount;

        double penalty;
        if (absenceRate <= 0.05) {
            penalty = 0.0;
        } else if (absenceRate <= 0.10) {
            penalty = 2.0;
        } else if (absenceRate <= 0.20) {
            penalty = 5.0;
        } else if (absenceRate <= 0.30) {
            penalty = 10.0;
        } else {
            penalty = 20.0;
        }

        log.info("出勤扣分计算：scheduleId={}, expectedCount={}, actualCount={}, absentCount={}, absenceRate={}, penalty={}",
                scheduleId, expectedCount, actualCount, absentCount, absenceRate, penalty);

        return penalty;
    }

    /**
     * 最终裁剪到 0~100
     */
    private double clampScore(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }
}
