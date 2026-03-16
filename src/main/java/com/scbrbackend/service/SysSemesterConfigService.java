package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.mapper.SysSemesterConfigMapper;
import com.scbrbackend.model.entity.SysSemesterConfig;
import com.scbrbackend.model.vo.SemesterConfigOptionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysSemesterConfigService extends ServiceImpl<SysSemesterConfigMapper, SysSemesterConfig> {

    /**
     * 获取学期配置下拉选项
     * 仅返回可用配置 (status = 1)，按学年倒序、学期倒序，后端直接组装 label
     */
    public Result<List<SemesterConfigOptionVO>> getOptions() {
        List<SysSemesterConfig> list = this.list(
                new LambdaQueryWrapper<SysSemesterConfig>()
                        .eq(SysSemesterConfig::getStatus, 1) // 仅返回可用的配置
                        .orderByDesc(SysSemesterConfig::getAcademicYear)
                        .orderByDesc(SysSemesterConfig::getSemester)
        );

        List<SemesterConfigOptionVO> options = list.stream().map(config -> {
            SemesterConfigOptionVO vo = new SemesterConfigOptionVO();
            BeanUtils.copyProperties(config, vo);
            
            // 组装 label，例如：2025-2026学年 第二学期
            String semesterStr = config.getSemester() == 1 ? "第一学期" : "第二学期";
            vo.setLabel(config.getAcademicYear() + "学年 " + semesterStr);
            
            return vo;
        }).collect(Collectors.toList());

        return Result.success("success", options);
    }
}
