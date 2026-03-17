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

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final AnalysisDetailMapper analysisDetailMapper;
    private final com.scbrbackend.service.AnalysisReportService analysisReportService;
    private final com.scbrbackend.mapper.AnalysisTaskMapper analysisTaskMapper;
    private final com.scbrbackend.mapper.CourseScheduleMapper courseScheduleMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            throw new BusinessException(404, "该任务尚未生成评估报告");
        }
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        java.util.Map<String, Object> basicInfo = new java.util.HashMap<>();
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
        
        java.math.BigDecimal attendanceRate = java.math.BigDecimal.ZERO;
        if (studentCount > 0) {
            attendanceRate = new java.math.BigDecimal(attendanceCount).divide(new java.math.BigDecimal(studentCount), 4, java.math.RoundingMode.HALF_UP).multiply(new java.math.BigDecimal(100));
        } else {
            attendanceRate = new java.math.BigDecimal("100.00");
        }
        if (attendanceRate.compareTo(new java.math.BigDecimal("100")) > 0) {
            attendanceRate = new java.math.BigDecimal("100.00");
        }
        
        java.math.BigDecimal attendanceScore = attendanceRate;
        java.math.BigDecimal totalScore = task.getTotalScore() != null ? task.getTotalScore() : java.math.BigDecimal.ZERO;
        
        java.math.BigDecimal focusScore = totalScore.multiply(new java.math.BigDecimal("0.9")).setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal interactionScore = totalScore.multiply(new java.math.BigDecimal("0.85")).setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal disciplineScore = totalScore.multiply(new java.math.BigDecimal("0.95")).setScale(2, java.math.RoundingMode.HALF_UP);
        
        String reportLevel = "需关注";
        if (totalScore.compareTo(new java.math.BigDecimal("90")) >= 0) reportLevel = "优秀";
        else if (totalScore.compareTo(new java.math.BigDecimal("80")) >= 0) reportLevel = "良好";
        else if (totalScore.compareTo(new java.math.BigDecimal("70")) >= 0) reportLevel = "一般";

        com.scbrbackend.model.entity.AnalysisReport report = analysisReportService.getByTaskId(taskId);
        boolean isUpdate = true;
        if (report == null) {
            isUpdate = false;
            report = new com.scbrbackend.model.entity.AnalysisReport();
            report.setTaskId(taskId);
            report.setScheduleId(task.getScheduleId());
        }
        
        report.setAttendanceRate(attendanceRate);
        report.setAttendanceScore(attendanceScore);
        report.setFocusScore(focusScore);
        report.setInteractionScore(interactionScore);
        report.setDisciplineScore(disciplineScore);
        report.setTotalScore(totalScore);
        report.setReportLevel(reportLevel);
        
        LambdaQueryWrapper<AnalysisDetail> abnormalQuery = new LambdaQueryWrapper<>();
        abnormalQuery.eq(AnalysisDetail::getTaskId, taskId)
                     .in(AnalysisDetail::getRecordType, java.util.Arrays.asList(2, 1))
                     .eq(AnalysisDetail::getBehaviorType, "玩手机");
        long abnormalCount = analysisDetailMapper.selectCount(abnormalQuery);
        report.setAbnormalFlag(abnormalCount > 0 ? 1 : 0);
        
        report.setSummaryText(reportLevel.equals("优秀") ? "本节课整体课堂秩序较好，学生整体专注度较高。" : "本节实时课堂整体表现尚可，但存在一定波动。");
        report.setSuggestionText("建议加强课堂关注度，并在课堂中段增加提问或讨论环节，以进一步提升学生互动。");
        
        report.setBehaviorStatsJson("{}");
        report.setTrendDataJson("[]");
        report.setAbnormalMomentsJson("[]");

        if (isUpdate) {
            analysisReportService.updateById(report);
        } else {
            analysisReportService.save(report);
        }
    }
}
