package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.AnalysisTaskMapper;
import com.scbrbackend.mapper.AnalysisTaskLogMapper;
import com.scbrbackend.mapper.TaskCenterMapper;
import com.scbrbackend.model.dto.*;
import com.scbrbackend.model.entity.AnalysisTask;
import com.scbrbackend.model.entity.AnalysisTaskLog;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskCenterService {

    @Autowired
    private TaskCenterMapper taskCenterMapper;
    
    @Autowired
    private AnalysisTaskMapper analysisTaskMapper;
    
    @Autowired
    private AnalysisTaskLogMapper analysisTaskLogMapper;
    
    @Autowired
    @Lazy
    private AnalysisTaskService analysisTaskService;

    public Map<String, Object> getTaskPage(TaskCenterPageQueryDTO query) {
        Page<TaskCenterRecordDTO> pageParams = new Page<>(query.getPage(), query.getSize());
        IPage<TaskCenterRecordDTO> pageResult = taskCenterMapper.selectTaskPage(pageParams, query);
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("records", pageResult.getRecords());
        return result;
    }

    public TaskCenterDetailDTO getTaskDetail(Long taskId, Long teacherId) {
        TaskCenterDetailDTO detail = taskCenterMapper.selectTaskDetail(taskId, teacherId);
        if (detail == null) {
            throw new BusinessException(404, "任务不存在或无权访问");
        }
        return detail;
    }

    public List<TaskLogItemDTO> getTaskLogs(Long taskId, Long teacherId) {
        // 先检查权限
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null || (teacherId != null && !task.getTeacherId().equals(teacherId))) {
            throw new BusinessException(404, "任务不存在或无权访问");
        }
        
        List<AnalysisTaskLog> logs = analysisTaskLogMapper.selectList(
                new LambdaQueryWrapper<AnalysisTaskLog>()
                        .eq(AnalysisTaskLog::getTaskId, taskId)
                        .orderByAsc(AnalysisTaskLog::getCreatedAt)
        );
        
        return logs.stream().map(logItem -> {
            TaskLogItemDTO dto = new TaskLogItemDTO();
            dto.setId(logItem.getId());
            dto.setStage(logItem.getStage());
            dto.setStatus(logItem.getStatus());
            dto.setMessage(logItem.getMessage());
            dto.setCreatedAt(logItem.getCreatedAt());
            if (logItem.getDetailJson() != null) {
                try {
                    dto.setDetailJson(JSON.parse(logItem.getDetailJson()));
                } catch (Exception e) {
                    dto.setDetailJson(logItem.getDetailJson());
                }
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public RetryTaskResponseDTO retryTask(Long taskId, Long teacherId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null || (teacherId != null && !task.getTeacherId().equals(teacherId))) {
            throw new BusinessException(404, "任务不存在或无权访问");
        }
        
        if (task.getStatus() != 3) {
            throw new BusinessException(500, "当前任务状态不是失败，禁止重复重试！");
        }
        
        // 重置状态
        task.setStatus(0);
        task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        task.setFailReason(null);
        task.setStartTime(null);
        task.setFinishTime(null);
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
        
        // 记录 RETRY 日志
        AnalysisTaskLog retryLog = new AnalysisTaskLog();
        retryLog.setTaskId(taskId);
        retryLog.setStage("RETRY");
        retryLog.setStatus(1);
        retryLog.setMessage("管理员或教师手动重试任务");
        Map<String, Object> detail = new HashMap<>();
        detail.put("retryCount", task.getRetryCount());
        retryLog.setDetailJson(JSON.toJSONString(detail));
        retryLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(retryLog);
        
        // 重新异步调用推理服务
        analysisTaskService.executeAiAnalysis(taskId);
        
        RetryTaskResponseDTO response = new RetryTaskResponseDTO();
        response.setTaskId(taskId);
        response.setStatus(0);
        response.setRetryCount(task.getRetryCount());
        return response;
    }
}
