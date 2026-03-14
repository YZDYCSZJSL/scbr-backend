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
}
