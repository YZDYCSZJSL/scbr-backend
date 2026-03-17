package com.scbrbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scbrbackend.model.entity.AnalysisReport;

public interface AnalysisReportService extends IService<AnalysisReport> {
    AnalysisReport getByTaskId(Long taskId);
}
