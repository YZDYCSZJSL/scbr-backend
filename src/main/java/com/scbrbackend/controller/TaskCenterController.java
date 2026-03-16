package com.scbrbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.TeacherMapper;
import com.scbrbackend.model.dto.*;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.service.TaskCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/task-center")
public class TaskCenterController {

    @Autowired
    private TaskCenterService taskCenterService;

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private com.scbrbackend.service.LogService logService;

    private Teacher getCurrentTeacher(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            throw new BusinessException(401, "未授权的访问！");
        }

        String token = authorization.trim();

        // 兼容前端传 Bearer
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        if (!token.startsWith("mock_jwt_token_")) {
            throw new BusinessException(401, "未授权的访问！");
        }

        String empNo = token.substring("mock_jwt_token_".length());

        Teacher teacher = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, empNo));

        if (teacher == null) {
            throw new BusinessException(401, "无效的用户令牌！");
        }

        return teacher;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> getTaskPage(TaskCenterPageQueryDTO query,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        if (teacher.getRole() == null || teacher.getRole() != 1) {
            // 普通教师限制只能看自己的
            query.setTeacherId(teacher.getId());
        } else {
            // 超级管理员可以看到所有
            query.setTeacherId(null);
        }
        Map<String, Object> pageData = taskCenterService.getTaskPage(query);
        return Result.success("success", pageData);
    }

    @GetMapping("/{taskId}")
    public Result<TaskCenterDetailDTO> getTaskDetail(@PathVariable Long taskId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        Long teacherId = (teacher.getRole() == null || teacher.getRole() != 1) ? teacher.getId() : null;
        TaskCenterDetailDTO detail = taskCenterService.getTaskDetail(taskId, teacherId);
        return Result.success("success", detail);
    }

    @GetMapping("/{taskId}/logs")
    public Result<List<TaskLogItemDTO>> getTaskLogs(@PathVariable Long taskId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        Long teacherId = (teacher.getRole() == null || teacher.getRole() != 1) ? teacher.getId() : null;
        List<TaskLogItemDTO> logs = taskCenterService.getTaskLogs(taskId, teacherId);
        return Result.success("success", logs);
    }

    @PostMapping("/{taskId}/retry")
    public Result<RetryTaskResponseDTO> retryTask(@PathVariable Long taskId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        Long teacherId = (teacher.getRole() == null || teacher.getRole() != 1) ? teacher.getId() : null;
        
        try {
            RetryTaskResponseDTO response = taskCenterService.retryTask(taskId, teacherId);
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "分析任务中心", "RETRY_TASK", taskId, "重试分析任务", 1);
            return Result.success("任务已重新加入分析队列，请稍后刷新查看状态！", response);
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "分析任务中心", "RETRY_TASK", taskId, "重试分析任务失败：" + msg, 0);
            throw e;
        }
    }
}
