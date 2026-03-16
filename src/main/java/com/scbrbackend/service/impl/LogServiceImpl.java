package com.scbrbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.mapper.AnalysisTaskLogMapper;
import com.scbrbackend.mapper.SysLoginLogMapper;
import com.scbrbackend.mapper.SysOperationLogMapper;
import com.scbrbackend.model.dto.LoginLogPageQueryDTO;
import com.scbrbackend.model.dto.OperationLogPageQueryDTO;
import com.scbrbackend.model.dto.TaskLogPageQueryDTO;
import com.scbrbackend.model.entity.AnalysisTaskLog;
import com.scbrbackend.model.entity.SysLoginLog;
import com.scbrbackend.model.entity.SysOperationLog;
import com.scbrbackend.model.vo.LoginLogPageVO;
import com.scbrbackend.model.vo.OperationLogPageVO;
import com.scbrbackend.model.vo.TaskLogPageVO;
import com.scbrbackend.service.LogService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LogServiceImpl implements LogService {

    @Autowired
    private SysLoginLogMapper sysLoginLogMapper;

    @Autowired
    private SysOperationLogMapper sysOperationLogMapper;

    @Autowired
    private AnalysisTaskLogMapper analysisTaskLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResult<LoginLogPageVO> getLoginLogPage(LoginLogPageQueryDTO query) {
        Page<SysLoginLog> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getEmpNo())) {
            wrapper.like(SysLoginLog::getEmpNo, query.getEmpNo());
        }
        if (StringUtils.hasText(query.getUserName())) {
            wrapper.like(SysLoginLog::getUserName, query.getUserName());
        }
        if (query.getLoginStatus() != null) {
            wrapper.eq(SysLoginLog::getLoginStatus, query.getLoginStatus());
        }
        if (StringUtils.hasText(query.getStartDate())) {
            wrapper.ge(SysLoginLog::getCreatedAt, query.getStartDate() + " 00:00:00");
        }
        if (StringUtils.hasText(query.getEndDate())) {
            wrapper.le(SysLoginLog::getCreatedAt, query.getEndDate() + " 23:59:59");
        }
        
        wrapper.orderByDesc(SysLoginLog::getCreatedAt);
        Page<SysLoginLog> resultPage = sysLoginLogMapper.selectPage(page, wrapper);

        List<LoginLogPageVO> voList = resultPage.getRecords().stream().map(record -> {
            LoginLogPageVO vo = new LoginLogPageVO();
            BeanUtils.copyProperties(record, vo);
            return vo;
        }).collect(Collectors.toList());

        PageResult<LoginLogPageVO> pageResult = new PageResult<>();
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPage(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setRecords(voList);

        return pageResult;
    }

    @Override
    public PageResult<OperationLogPageVO> getOperationLogPage(OperationLogPageQueryDTO query) {
        Page<SysOperationLog> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getEmpNo())) {
            wrapper.like(SysOperationLog::getEmpNo, query.getEmpNo());
        }
        if (StringUtils.hasText(query.getModuleName())) {
            wrapper.eq(SysOperationLog::getModuleName, query.getModuleName());
        }
        if (StringUtils.hasText(query.getOperationType())) {
            wrapper.eq(SysOperationLog::getOperationType, query.getOperationType());
        }
        if (query.getOperationStatus() != null) {
            wrapper.eq(SysOperationLog::getOperationStatus, query.getOperationStatus());
        }
        if (StringUtils.hasText(query.getStartDate())) {
            wrapper.ge(SysOperationLog::getCreatedAt, query.getStartDate() + " 00:00:00");
        }
        if (StringUtils.hasText(query.getEndDate())) {
            wrapper.le(SysOperationLog::getCreatedAt, query.getEndDate() + " 23:59:59");
        }
        
        wrapper.orderByDesc(SysOperationLog::getCreatedAt);
        Page<SysOperationLog> resultPage = sysOperationLogMapper.selectPage(page, wrapper);

        List<OperationLogPageVO> voList = resultPage.getRecords().stream().map(record -> {
            OperationLogPageVO vo = new OperationLogPageVO();
            BeanUtils.copyProperties(record, vo);
            return vo;
        }).collect(Collectors.toList());

        PageResult<OperationLogPageVO> pageResult = new PageResult<>();
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPage(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setRecords(voList);

        return pageResult;
    }

    @Override
    public PageResult<TaskLogPageVO> getTaskLogPage(TaskLogPageQueryDTO query) {
        Page<AnalysisTaskLog> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<AnalysisTaskLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getTaskId() != null) {
            wrapper.eq(AnalysisTaskLog::getTaskId, query.getTaskId());
        }
        if (StringUtils.hasText(query.getStage())) {
            wrapper.eq(AnalysisTaskLog::getStage, query.getStage());
        }
        if (query.getStatus() != null) {
            wrapper.eq(AnalysisTaskLog::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getStartDate())) {
            wrapper.ge(AnalysisTaskLog::getCreatedAt, query.getStartDate() + " 00:00:00");
        }
        if (StringUtils.hasText(query.getEndDate())) {
            wrapper.le(AnalysisTaskLog::getCreatedAt, query.getEndDate() + " 23:59:59");
        }

        wrapper.orderByDesc(AnalysisTaskLog::getCreatedAt);
        Page<AnalysisTaskLog> resultPage = analysisTaskLogMapper.selectPage(page, wrapper);

        List<TaskLogPageVO> voList = resultPage.getRecords().stream().map(record -> {
            TaskLogPageVO vo = new TaskLogPageVO();
            BeanUtils.copyProperties(record, vo);
            if (StringUtils.hasText(record.getDetailJson())) {
                try {
                    vo.setDetailJson(objectMapper.readTree(record.getDetailJson()));
                } catch (Exception e) {
                    vo.setDetailJson(record.getDetailJson());
                }
            }
            return vo;
        }).collect(Collectors.toList());

        PageResult<TaskLogPageVO> pageResult = new PageResult<>();
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPage(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setRecords(voList);

        return pageResult;
    }

    @Override
    public void recordLoginLog(SysLoginLog log) {
        sysLoginLogMapper.insert(log);
    }

    @Override
    public void recordOperationLog(SysOperationLog log) {
        sysOperationLogMapper.insert(log);
    }
}
