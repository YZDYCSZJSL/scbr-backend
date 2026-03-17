package com.scbrbackend.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.AnalysisDetailMapper;
import com.scbrbackend.mapper.ReportMapper;
import com.scbrbackend.model.dto.ReportExportDTO;
import com.scbrbackend.model.dto.ReportPageQueryDTO;
import com.scbrbackend.model.entity.AnalysisDetail;
import com.scbrbackend.model.vo.AnalysisDetailVO;
import com.scbrbackend.model.vo.ReportDetailVO;
import com.scbrbackend.model.vo.ReportPageVO;
import com.scbrbackend.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final AnalysisDetailMapper analysisDetailMapper;
    private final com.scbrbackend.service.AnalysisReportService analysisReportService;
    private final com.scbrbackend.mapper.AnalysisTaskMapper analysisTaskMapper;
    private final com.scbrbackend.mapper.CourseScheduleMapper courseScheduleMapper;
    private final com.scbrbackend.mapper.SysWeightConfigMapper sysWeightConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Double> parseWeightConfig(String content) {
        Map<String, Double> weightMap = new java.util.HashMap<>();
        if (content == null || content.isEmpty()) return weightMap;
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has("behaviorType") && node.has("weight")) {
                        weightMap.put(node.get("behaviorType").asText().trim(), node.get("weight").asDouble());
                    }
                }
            } else if (root.isObject()) {
                weightMap = objectMapper.readValue(content, new TypeReference<Map<String, Double>>() {});
            }
        } catch (Exception e) {}
        return weightMap;
    }

    private double getW(Map<String, Double> weightMap, String key) {
        return weightMap.getOrDefault(key, 0.0);
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private String buildSummaryText(com.scbrbackend.model.entity.AnalysisReport report) {
        if (report == null) return "暂无评估摘要。";

        double totalScore = report.getTotalScore() != null ? report.getTotalScore().doubleValue() : 0.0;
        double attendanceScore = report.getAttendanceScore() != null ? report.getAttendanceScore().doubleValue() : 0.0;
        double focusScore = report.getFocusScore() != null ? report.getFocusScore().doubleValue() : 0.0;
        double interactionScore = report.getInteractionScore() != null ? report.getInteractionScore().doubleValue() : 0.0;
        double disciplineScore = report.getDisciplineScore() != null ? report.getDisciplineScore().doubleValue() : 0.0;
        String reportLevel = report.getReportLevel() != null ? report.getReportLevel() : "一般";

        StringBuilder sb = new StringBuilder();

        // 1. 总评
        sb.append(String.format("本节课综合评分为 %.1f 分，评估等级为“%s”。", totalScore, reportLevel));

        // 2. 出勤情况
        if (attendanceScore >= 90) {
            sb.append("课堂出勤情况良好，学生到课率较高。");
        } else if (attendanceScore >= 80) {
            sb.append("课堂出勤情况表现良好，基本按时到课。");
        } else if (attendanceScore >= 70) {
            sb.append("课堂出勤情况基本正常，但仍存在一定缺勤现象。");
        } else {
            sb.append("课堂出勤情况较弱，缺勤问题较明显。");
        }

        // 3. 专注情况
        if (focusScore >= 90) {
            sb.append("学生整体专注度较高，课堂学习状态很好。");
        } else if (focusScore >= 80) {
            sb.append("学生专注度表现良好，多数能跟上教学节奏。");
        } else if (focusScore >= 70) {
            sb.append("学生专注度一般，部分时段存在分心现象。");
        } else {
            sb.append("学生专注度偏低，分心或不良学习行为较明显。");
        }

        // 4. 互动情况
        if (interactionScore >= 90) {
            sb.append("课堂互动十分积极，学生参与度很高。");
        } else if (interactionScore >= 80) {
            sb.append("课堂互动较为积极，学生参与度较高。");
        } else if (interactionScore >= 70) {
            sb.append("课堂互动一般，个别学生能主动参与。");
        } else {
            sb.append("课堂互动偏弱，学生主动参与程度不足。");
        }

        // 5. 纪律情况
        if (disciplineScore >= 90) {
            sb.append("课堂纪律总体非常优秀，无异常行为。");
        } else if (disciplineScore >= 80) {
            sb.append("课堂纪律总体较好，异常行为极少。");
        } else if (disciplineScore >= 70) {
            sb.append("课堂纪律基本可控，偶尔出现违纪行为。");
        } else {
            sb.append("课堂纪律情况较弱，异常行为较明显，需重点关注。");
        }

        return sb.toString();
    }

    private String buildSuggestionText(com.scbrbackend.model.entity.AnalysisReport report) {
        if (report == null) return "暂无建议。";

        double attendanceScore = report.getAttendanceScore() != null ? report.getAttendanceScore().doubleValue() : 0.0;
        double focusScore = report.getFocusScore() != null ? report.getFocusScore().doubleValue() : 0.0;
        double interactionScore = report.getInteractionScore() != null ? report.getInteractionScore().doubleValue() : 0.0;
        double disciplineScore = report.getDisciplineScore() != null ? report.getDisciplineScore().doubleValue() : 0.0;

        java.util.List<String> suggestions = new java.util.ArrayList<>();

        if (attendanceScore < 85) {
            suggestions.add("出勤管理：建议加强考勤管理，重点关注缺勤现象并了解缺勤原因。");
        }
        if (focusScore < 80) {
            suggestions.add("专注度提升：建议优化课堂节奏，适时穿插生动案例或练习以提升学生专注度。");
        }
        if (interactionScore < 75) {
            suggestions.add("课堂互动：建议增加课堂提问、小组讨论或互动设计，激发学生的参与热情。");
        }
        if (disciplineScore < 80) {
            suggestions.add("纪律管理：建议加强课堂纪律把控，对分心或异常行为及时进行干预和提醒。");
        }

        if (suggestions.isEmpty()) {
            return "本节课整体表现较好，建议继续保持当前教学组织方式，并适当加入互动环节提升课堂活力。";
        }

        return String.join("\n", suggestions);
    }

    @Override
    public IPage<ReportPageVO> getPage(ReportPageQueryDTO query, Long currentTeacherId) {
        Page<ReportPageVO> pageParam = new Page<>(query.getPage(), query.getSize());
        return reportMapper.selectReportPage(pageParam, query, currentTeacherId);
    }

    @Override
    public void exportByIds(ReportExportDTO dto, HttpServletResponse response, Long currentTeacherId) {
        List<Long> ids = dto.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请选择要导出的记录");
        }
        
        // 1. 获取任务的主信息
        List<ReportPageVO> rawList = reportMapper.selectReportExport(ids, currentTeacherId);
        if (rawList.isEmpty()) {
            throw new BusinessException(404, "根据提供的ID未找到相关的报表记录");
        }

        // 2. 提取本批次内所有出现过的行为类型
        LambdaQueryWrapper<AnalysisDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(AnalysisDetail::getTaskId, ids);
        List<AnalysisDetail> allDetails = analysisDetailMapper.selectList(detailWrapper);
        
        // 分组归类：获取本批次涉及到的所有"行为名称(行为类型关键字)"
        // 为了保证通用性，如果 config 里面存在的类型这里没出现也可以查，但最简单的是拿本次出现的类别
        List<String> dynamicBehaviorTypes = allDetails.stream()
                .map(AnalysisDetail::getBehaviorType)
                .distinct()
                .collect(Collectors.toList());

        // 为了中文化，可以简单做个内置映射，或者使用原始 behaviorType
        java.util.Map<String, String> typeNameMap = new java.util.HashMap<>();
        typeNameMap.put("listening", "听讲人数");
        typeNameMap.put("playing_phone", "玩手机人数");
        typeNameMap.put("sleeping", "睡觉人数");
        typeNameMap.put("raising_hand", "举手人数");

        // 3. 构建动态表头头
        List<List<String>> headers = new java.util.ArrayList<>();
        headers.add(java.util.Arrays.asList("任务ID"));
        headers.add(java.util.Arrays.asList("课程名称"));
        headers.add(java.util.Arrays.asList("教师姓名"));
        headers.add(java.util.Arrays.asList("教室名称"));
        headers.add(java.util.Arrays.asList("分析模式"));
        headers.add(java.util.Arrays.asList("创建时间"));
        headers.add(java.util.Arrays.asList("状态"));
        headers.add(java.util.Arrays.asList("实到人数"));
        headers.add(java.util.Arrays.asList("综合得分"));

        // 追加动态表头
        for (String bType : dynamicBehaviorTypes) {
            String colName = typeNameMap.getOrDefault(bType, bType + "人数");
            headers.add(java.util.Arrays.asList(colName));
        }

        // 4. 将所有明细按照 taskId 分组，方便装填
        java.util.Map<Long, List<AnalysisDetail>> detailMap = allDetails.stream()
                .collect(Collectors.groupingBy(AnalysisDetail::getTaskId));

        // 5. 构建动态数据行
        List<List<Object>> dataList = new java.util.ArrayList<>();
        for (ReportPageVO raw : rawList) {
            List<Object> row = new java.util.ArrayList<>();
            row.add(raw.getId());
            row.add(raw.getCourseName());
            row.add(raw.getTeacherName());
            row.add(raw.getClassroomName());
            
            String mediaTypeStr = "未知";
            if (raw.getMediaType() != null) {
                if (raw.getMediaType() == 1) mediaTypeStr = "图片";
                else if (raw.getMediaType() == 2) mediaTypeStr = "视频";
                else if (raw.getMediaType() == 3) mediaTypeStr = "实时流";
            }
            row.add(mediaTypeStr);
            
            String createdAtStr = "";
            if (raw.getCreatedAt() != null) {
                createdAtStr = raw.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            row.add(createdAtStr);
            
            String statusStr = "未知";
            if (raw.getStatus() != null) {
                if (raw.getStatus() == 0) statusStr = "排队中";
                else if (raw.getStatus() == 1) statusStr = "分析中";
                else if (raw.getStatus() == 2) statusStr = "成功";
                else if (raw.getStatus() == 3) statusStr = "失败";
            }
            row.add(statusStr);
            
            row.add(raw.getAttendanceCount());
            row.add(raw.getTotalScore());

            // 填充动态行为人数
            List<AnalysisDetail> currentDetails = detailMap.getOrDefault(raw.getId(), new java.util.ArrayList<>());
            java.util.Map<String, Integer> currentCountMap = currentDetails.stream()
                    .collect(Collectors.groupingBy(AnalysisDetail::getBehaviorType, 
                             Collectors.summingInt(AnalysisDetail::getCount)));

            for (String bType : dynamicBehaviorTypes) {
                row.add(currentCountMap.getOrDefault(bType, 0));
            }
            
            dataList.add(row);
        }

        // 6. 写入 Excel 响应流
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("课堂行为分析报表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            
            EasyExcel.write(response.getOutputStream())
                    .head(headers)
                    .sheet("报表数据")
                    .doWrite(dataList);
        } catch (IOException e) {
            throw new BusinessException(500, "导出Excel失败: " + e.getMessage());
        }
    }

    @Override
    public ReportDetailVO getDetailById(Long id, Long currentTeacherId) {
        ReportDetailVO detailVO = reportMapper.selectReportDetailById(id, currentTeacherId);
        if (detailVO == null) {
            throw new BusinessException(404, "报表记录不存在");
        }
        
        LambdaQueryWrapper<AnalysisDetail> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AnalysisDetail::getTaskId, id);
        List<AnalysisDetail> detailList = analysisDetailMapper.selectList(queryWrapper);
        ObjectMapper mapper = new ObjectMapper();
        
        List<AnalysisDetailVO> voList = detailList.stream().map(d -> {
            AnalysisDetailVO vo = new AnalysisDetailVO();
            vo.setRecordType(d.getRecordType());
            vo.setFrameTime(d.getFrameTime());
            vo.setBehaviorType(d.getBehaviorType());
            vo.setCount(d.getCount());
            vo.setSnapshotUrl(d.getSnapshotUrl());
            if (d.getBoundingBoxes() != null && !d.getBoundingBoxes().isEmpty()) {
                try {
                    vo.setBoundingBoxes(mapper.readValue(d.getBoundingBoxes(), new TypeReference<Object>() {}));
                } catch (Exception e) {
                    vo.setBoundingBoxes(null);
                }
            }
            return vo;
        }).collect(Collectors.toList());
        
        detailVO.setDetailList(voList);
        return detailVO;
    }

    @Override
    public java.util.Map<String, Object> getEvaluation(Long taskId, Long currentTeacherId) {
        com.scbrbackend.model.entity.AnalysisReport report = analysisReportService.getByTaskId(taskId);
        if (report == null) {
            // 先确认任务是否已完成
            com.scbrbackend.model.entity.AnalysisTask task = analysisTaskMapper.selectById(taskId);
            if (task == null) {
                throw new BusinessException(404, "任务不存在");
            }
            if (task.getStatus() == null || task.getStatus() != 2) {
                throw new BusinessException(400, "该任务尚未分析完成，暂不能生成评估报告");
            }

            // 自动生成一次
            generateReport(taskId, currentTeacherId);

            // 重新查询
            report = analysisReportService.getByTaskId(taskId);
            if (report == null) {
                throw new BusinessException(500, "评估报告自动生成失败");
            }
        }
        
        ReportDetailVO detail = reportMapper.selectReportDetailById(taskId, currentTeacherId);
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        java.util.Map<String, Object> basicInfo = new java.util.HashMap<>();
        basicInfo.put("courseName", detail != null ? detail.getCourseName() : null);
        basicInfo.put("teacherName", detail != null ? detail.getTeacherName() : null);
        basicInfo.put("classroomName", detail != null ? detail.getClassroomName() : null);
        basicInfo.put("classTimeText", detail != null ? detail.getClassTimeText() : null);
        basicInfo.put("studentCount", detail != null ? detail.getStudentCount() : null);
        basicInfo.put("attendanceCount", detail != null ? detail.getAttendanceCount() : null);
        basicInfo.put("mediaType", detail != null ? detail.getMediaType() : null);
        basicInfo.put("durationSeconds", detail != null ? detail.getDurationSeconds() : null);
        basicInfo.put("reportLevel", report.getReportLevel());
        basicInfo.put("abnormalFlag", report.getAbnormalFlag());
        basicInfo.put("attendanceRate", report.getAttendanceRate());
        result.put("basicInfo", basicInfo);
        
        java.util.Map<String, Object> scores = new java.util.HashMap<>();
        scores.put("attendanceScore", report.getAttendanceScore());
        scores.put("focusScore", report.getFocusScore());
        scores.put("interactionScore", report.getInteractionScore());
        scores.put("disciplineScore", report.getDisciplineScore());
        scores.put("totalScore", report.getTotalScore());
        result.put("scores", scores);
        
        try {
            if (report.getBehaviorStatsJson() != null && !report.getBehaviorStatsJson().isEmpty()) {
                result.put("behaviorStats", objectMapper.readValue(report.getBehaviorStatsJson(), Object.class));
            } else {
                result.put("behaviorStats", new java.util.ArrayList<>());
            }
            if (report.getTrendDataJson() != null && !report.getTrendDataJson().isEmpty()) {
                result.put("trendData", objectMapper.readValue(report.getTrendDataJson(), Object.class));
            } else {
                 result.put("trendData", new java.util.ArrayList<>());
            }
            if (report.getAbnormalMomentsJson() != null && !report.getAbnormalMomentsJson().isEmpty()) {
                result.put("abnormalMoments", objectMapper.readValue(report.getAbnormalMomentsJson(), Object.class));
            } else {
                 result.put("abnormalMoments", new java.util.ArrayList<>());
            }
        } catch (Exception e) {
            // ignore
        }
        
        result.put("summary", report.getSummaryText());
        result.put("suggestions", java.util.Arrays.asList(report.getSuggestionText() != null ? report.getSuggestionText().split("\n") : new String[0]));
        
        return result;
    }

    @Override
    public java.util.List<Object> getTrend(Long taskId, Long currentTeacherId) {
        com.scbrbackend.model.entity.AnalysisReport report = analysisReportService.getByTaskId(taskId);
        if (report == null || report.getTrendDataJson() == null || report.getTrendDataJson().isEmpty()) return new java.util.ArrayList<>();
        try {
            return objectMapper.readValue(report.getTrendDataJson(), new TypeReference<java.util.List<Object>>() {});
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    @Override
    public java.util.List<Object> getAbnormalSnapshots(Long taskId, Long currentTeacherId) {
        com.scbrbackend.model.entity.AnalysisReport report = analysisReportService.getByTaskId(taskId);
        if (report == null || report.getAbnormalMomentsJson() == null || report.getAbnormalMomentsJson().isEmpty()) return new java.util.ArrayList<>();
        try {
            return objectMapper.readValue(report.getAbnormalMomentsJson(), new TypeReference<java.util.List<Object>>() {});
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    @Override
    public void generateReport(Long taskId, Long currentTeacherId) {
        com.scbrbackend.model.entity.AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "分析任务不存在");
        }
        com.scbrbackend.model.entity.CourseSchedule schedule = courseScheduleMapper.selectById(task.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(404, "排课信息不存在");
        }

        int studentCount = schedule.getStudentCount() == null ? 0 : schedule.getStudentCount();
        int attendanceCount = task.getAttendanceCount() == null ? 0 : task.getAttendanceCount();
        
        java.math.BigDecimal attendanceScoreVal = java.math.BigDecimal.ZERO;
        if (studentCount > 0) {
            attendanceScoreVal = new java.math.BigDecimal(attendanceCount).divide(new java.math.BigDecimal(studentCount), 4, java.math.RoundingMode.HALF_UP).multiply(new java.math.BigDecimal(100));
        } else {
            attendanceScoreVal = new java.math.BigDecimal("100.00");
        }
        if (attendanceScoreVal.compareTo(new java.math.BigDecimal("100")) > 0) {
            attendanceScoreVal = new java.math.BigDecimal("100.00");
        }
        
        LambdaQueryWrapper<AnalysisDetail> detailQuery = new LambdaQueryWrapper<>();
        detailQuery.eq(AnalysisDetail::getTaskId, taskId);
        List<AnalysisDetail> details = analysisDetailMapper.selectList(detailQuery);

        com.scbrbackend.model.entity.SysWeightConfig activeConfig = sysWeightConfigMapper.selectOne(
                new LambdaQueryWrapper<com.scbrbackend.model.entity.SysWeightConfig>().eq(com.scbrbackend.model.entity.SysWeightConfig::getIsActive, 1));
        Map<String, Double> weightMap = parseWeightConfig(activeConfig != null ? activeConfig.getConfigContent() : "[]");

        java.util.Set<Integer> uniqueFrames = new java.util.HashSet<>();
        Map<Integer, Map<String, Integer>> frameSumMap = new java.util.HashMap<>();
        int snapshotCount = 0;
        double snapshotPenalty = 0.0;
        
        for (AnalysisDetail d : details) {
            if (d.getRecordType() == 2 && d.getSnapshotUrl() != null && !d.getSnapshotUrl().isEmpty()) {
                snapshotCount++;
                double w = getW(weightMap, d.getBehaviorType());
                snapshotPenalty += w;
                continue;
            }
            if (d.getRecordType() == 0 || d.getRecordType() == 1) {
                int ft = d.getFrameTime() == null ? 0 : d.getFrameTime();
                uniqueFrames.add(ft);
                frameSumMap.putIfAbsent(ft, new java.util.HashMap<>());
                Map<String, Integer> bMap = frameSumMap.get(ft);
                bMap.put(d.getBehaviorType(), bMap.getOrDefault(d.getBehaviorType(), 0) + (d.getCount() == null ? 0 : d.getCount()));
            }
        }
        
        Map<String, Double> avgCounts = new java.util.HashMap<>();
        int frameCount = uniqueFrames.isEmpty() ? 1 : uniqueFrames.size();
        for (Map<String, Integer> bMap : frameSumMap.values()) {
            for (Map.Entry<String, Integer> e : bMap.entrySet()) {
                avgCounts.put(e.getKey(), avgCounts.getOrDefault(e.getKey(), 0.0) + e.getValue());
            }
        }
        for (Map.Entry<String, Double> e : avgCounts.entrySet()) {
            avgCounts.put(e.getKey(), e.getValue() / frameCount);
        }

        double wFocus1 = getW(weightMap, "正常听课");
        double wFocus2 = getW(weightMap, "阅读");
        double wFocus3 = getW(weightMap, "书写");
        double wFocus4 = getW(weightMap, "玩手机");
        double wFocus5 = getW(weightMap, "趴桌");
        boolean focusAllZero = (wFocus1 == 0 && wFocus2 == 0 && wFocus3 == 0 && wFocus4 == 0 && wFocus5 == 0);
        double focusImpact = avgCounts.getOrDefault("正常听课", 0.0) * wFocus1 +
                avgCounts.getOrDefault("阅读", 0.0) * wFocus2 +
                avgCounts.getOrDefault("书写", 0.0) * wFocus3 +
                avgCounts.getOrDefault("玩手机", 0.0) * wFocus4 +
                avgCounts.getOrDefault("趴桌", 0.0) * wFocus5;
        double focusScoreVal = focusAllZero ? 100.0 : clamp(100.0 + focusImpact, 0, 100);

        double wInt1 = getW(weightMap, "举手回答问题");
        if (wInt1 == 0.0) wInt1 = getW(weightMap, "举手");
        double wInt2 = getW(weightMap, "起立回答问题");
        if (wInt2 == 0.0) wInt2 = getW(weightMap, "起立");
        boolean intAllZero = (wInt1 == 0 && wInt2 == 0);
        double intImpact = avgCounts.getOrDefault("举手回答问题", avgCounts.getOrDefault("举手", 0.0)) * wInt1 +
                avgCounts.getOrDefault("起立回答问题", avgCounts.getOrDefault("起立", 0.0)) * wInt2;
        double interactionScoreVal = intAllZero ? 100.0 : clamp(0.0 + intImpact, 0, 100);

        boolean discAllZero = (wFocus4 == 0 && wFocus5 == 0);
        double discImpact = avgCounts.getOrDefault("玩手机", 0.0) * wFocus4 +
                avgCounts.getOrDefault("趴桌", 0.0) * wFocus5 +
                snapshotPenalty;
        double disciplineScoreVal = discAllZero ? 100.0 : clamp(100.0 + discImpact, 0, 100);

        double totalScoreVal = 0.25 * attendanceScoreVal.doubleValue() +
                0.35 * focusScoreVal +
                0.15 * interactionScoreVal +
                0.25 * disciplineScoreVal;
        totalScoreVal = clamp(totalScoreVal, 0, 100);

        String reportLevel = "需关注";
        if (totalScoreVal >= 90) reportLevel = "优秀";
        else if (totalScoreVal >= 80) reportLevel = "良好";
        else if (totalScoreVal >= 70) reportLevel = "一般";

        int abnormalFlag = 0;
        if (avgCounts.getOrDefault("玩手机", 0.0) >= 1.0 ||
                avgCounts.getOrDefault("趴桌", 0.0) >= 1.0 ||
                snapshotCount > 0) {
            abnormalFlag = 1;
        }

        com.scbrbackend.model.entity.AnalysisReport report = analysisReportService.getByTaskId(taskId);
        boolean isUpdate = true;
        if (report == null) {
            isUpdate = false;
            report = new com.scbrbackend.model.entity.AnalysisReport();
            report.setTaskId(taskId);
            report.setScheduleId(task.getScheduleId());
        }
        
        report.setAttendanceRate(attendanceScoreVal);
        report.setAttendanceScore(attendanceScoreVal);
        report.setFocusScore(new java.math.BigDecimal(focusScoreVal).setScale(2, java.math.RoundingMode.HALF_UP));
        report.setInteractionScore(new java.math.BigDecimal(interactionScoreVal).setScale(2, java.math.RoundingMode.HALF_UP));
        report.setDisciplineScore(new java.math.BigDecimal(disciplineScoreVal).setScale(2, java.math.RoundingMode.HALF_UP));
        report.setTotalScore(new java.math.BigDecimal(totalScoreVal).setScale(2, java.math.RoundingMode.HALF_UP));
        report.setReportLevel(reportLevel);
        report.setAbnormalFlag(abnormalFlag);
        
        report.setSummaryText(buildSummaryText(report));
        report.setSuggestionText(buildSuggestionText(report));
        
        try {
            // 1. behaviorStatsJson
            java.util.Map<String, java.util.Map<String, Object>> statsMap = new java.util.HashMap<>();
            for (AnalysisDetail d : details) {
                String type = d.getBehaviorType();
                if (type == null) continue;
                statsMap.putIfAbsent(type, new java.util.HashMap<>(java.util.Map.of("behaviorType", type, "totalCount", 0, "peakCount", 0, "ratio", "0%")));
                java.util.Map<String, Object> stat = statsMap.get(type);
                int currentTotal = (int) stat.get("totalCount") + (d.getCount() != null ? d.getCount() : 0);
                int currentPeak = Math.max((int) stat.get("peakCount"), (d.getCount() != null ? d.getCount() : 0));
                stat.put("totalCount", currentTotal);
                stat.put("peakCount", currentPeak);
            }
            int allCount = statsMap.values().stream().mapToInt(v -> (int) v.get("totalCount")).sum();
            for (java.util.Map<String, Object> stat : statsMap.values()) {
                if (allCount > 0) {
                    double ratio = (double) ((int) stat.get("totalCount")) / allCount * 100;
                    stat.put("ratio", String.format("%.2f%%", ratio));
                }
            }
            report.setBehaviorStatsJson(objectMapper.writeValueAsString(new java.util.ArrayList<>(statsMap.values())));
            
            // 2. trendDataJson
            if (task.getMediaType() != null && task.getMediaType() == 1) {
                report.setTrendDataJson("[]");
            } else {
                java.util.Map<String, java.util.Map<String, Object>> trendMap = new java.util.TreeMap<>();
                for (AnalysisDetail d : details) {
                    if (d.getFrameTime() == null) continue;
                    int totalSecs = d.getFrameTime();
                    String timeStr = String.format("%02d:%02d:%02d", totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60);
                    trendMap.putIfAbsent(timeStr, new java.util.HashMap<>(java.util.Map.of("frameTime", timeStr)));
                    java.util.Map<String, Object> point = trendMap.get(timeStr);
                    point.put(d.getBehaviorType(), (int) point.getOrDefault(d.getBehaviorType(), 0) + (d.getCount() != null ? d.getCount() : 0));
                }
                report.setTrendDataJson(objectMapper.writeValueAsString(new java.util.ArrayList<>(trendMap.values())));
            }
            
            // 3. abnormalMomentsJson
            List<java.util.Map<String, Object>> abnormalList = new java.util.ArrayList<>();
            for (AnalysisDetail d : details) {
                if (d.getSnapshotUrl() != null && !d.getSnapshotUrl().isEmpty()) {
                    java.util.Map<String, Object> am = new java.util.HashMap<>();
                    if (d.getFrameTime() != null) {
                        int totalSecs = d.getFrameTime();
                        am.put("frameTime", String.format("%02d:%02d:%02d", totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60));
                    } else {
                        am.put("frameTime", null);
                    }
                    am.put("behaviorType", d.getBehaviorType());
                    am.put("count", d.getCount());
                    am.put("snapshotUrl", d.getSnapshotUrl());
                    abnormalList.add(am);
                }
            }
            report.setAbnormalMomentsJson(objectMapper.writeValueAsString(abnormalList));
            
        } catch (Exception e) {
            report.setBehaviorStatsJson("[]");
            report.setTrendDataJson("[]");
            report.setAbnormalMomentsJson("[]");
        }

        if (isUpdate) {
            analysisReportService.updateById(report);
        } else {
            analysisReportService.save(report);
        }
    }
}
