package com.scbrbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.scbrbackend.model.dto.ReportPageQueryDTO;
import com.scbrbackend.model.entity.AnalysisTask;
import com.scbrbackend.model.vo.ReportDetailVO;
import com.scbrbackend.model.vo.ReportPageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper extends BaseMapper<AnalysisTask> {

    IPage<ReportPageVO> selectReportPage(IPage<ReportPageVO> page, @Param("query") ReportPageQueryDTO query, @Param("currentTeacherId") Long currentTeacherId);

    List<ReportPageVO> selectReportExport(@Param("ids") List<Long> ids, @Param("currentTeacherId") Long currentTeacherId);

    ReportDetailVO selectReportDetailById(@Param("id") Long id, @Param("currentTeacherId") Long currentTeacherId);
}
