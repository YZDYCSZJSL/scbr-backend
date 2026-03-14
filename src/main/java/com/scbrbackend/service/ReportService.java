package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.scbrbackend.model.dto.ReportExportDTO;
import com.scbrbackend.model.dto.ReportPageQueryDTO;
import com.scbrbackend.model.vo.ReportDetailVO;
import com.scbrbackend.model.vo.ReportPageVO;
import jakarta.servlet.http.HttpServletResponse;

public interface ReportService {
    IPage<ReportPageVO> getPage(ReportPageQueryDTO query, Long currentTeacherId);
    void exportByIds(ReportExportDTO dto, HttpServletResponse response, Long currentTeacherId);
    ReportDetailVO getDetailById(Long id, Long currentTeacherId);
}
