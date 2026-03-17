package com.scbrbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.mapper.AnalysisReportMapper;
import com.scbrbackend.model.entity.AnalysisReport;
import com.scbrbackend.service.AnalysisReportService;
import org.springframework.stereotype.Service;

@Service
public class AnalysisReportServiceImpl extends ServiceImpl<AnalysisReportMapper, AnalysisReport> implements AnalysisReportService {
    @Override
    public AnalysisReport getByTaskId(Long taskId) {
        return this.getOne(new LambdaQueryWrapper<AnalysisReport>().eq(AnalysisReport::getTaskId, taskId));
    }
}
