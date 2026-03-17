package com.scbrbackend.controller;

import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.AnalysisTaskDetailDTO;
import com.scbrbackend.service.AnalysisTaskService;
import com.scbrbackend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import com.scbrbackend.model.dto.AnalysisTaskStatusDTO;
import com.scbrbackend.model.dto.CreateAnalysisTaskResponseDTO;

@RestController
@RequestMapping("/api/v1/analysis/task")
public class AnalysisTaskController {

    @Autowired
    private AnalysisTaskService analysisTaskService;

    @Autowired
    private com.scbrbackend.service.LogService logService;

    @PostMapping
    public Result<CreateAnalysisTaskResponseDTO> createAnalysisTask(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileName") String fileName,
            @RequestParam("scheduleId") Long scheduleId,
            @RequestParam("streamType") Integer streamType,
            HttpServletRequest request) {

        // 如果 token 在这主要用作传给模型或后续验证等，但我们内部改成了 UserContext，
        // 实际上 analysisTaskService.createAnalysisTask 里面也有一段解析，等下我们要去改服务层吗？
        // 由于任务明确“尽量不改其他多余层”，如果 analysisTaskService.createAnalysisTask 原本接收的是 token 并且它内部去解析(如果需要)。
        // 为了平滑过渡且不增加额外修改，如果下游只需要 token 字符串或者下游不抛异常，我们可以用当前请求头部的原始 token 或 JWT（直接透传 requestHeader 里的 JWT）。
        // 建议传入 JWT token 原字符，但在本控制器中我们还是按原代码透传。
        
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 1. 同步进行 OSS 上传与数据库落库
        Result<CreateAnalysisTaskResponseDTO> result = analysisTaskService.createAnalysisTask(
                file, fileName, scheduleId, streamType, token);

        // 2. 落库成功后，立即返回给前端 HTTP 200，并使用 @Async 或新线程 触发真正的 AI 识别逻辑。
        if (result.getCode() == 200 && result.getData() != null) {
            Long taskId = result.getData().getTaskId();
            analysisTaskService.executeAiAnalysis(taskId);
        }

        return result;
    }

    @GetMapping("/{taskId}/status")
    public Result<AnalysisTaskStatusDTO> getTaskStatus(@PathVariable("taskId") Long taskId) {
        AnalysisTaskStatusDTO statusDTO = analysisTaskService.getTaskStatus(taskId);
        if (statusDTO == null) {
            return Result.error(404, "Task not found");
        }
        return Result.success(statusDTO);
    }

    @PutMapping("/{taskId}/stop")
    public Result<Object> stopTask(@PathVariable("taskId") Long taskId,
            jakarta.servlet.http.HttpServletRequest request) {
        com.scbrbackend.common.context.CurrentUser user = com.scbrbackend.common.context.UserContext.getCurrentUser();
        String empNo = user.getEmpNo();
        String teacherName = user.getName();

        try {
            analysisTaskService.stopAnalysisTask(taskId);
            // 此类操作日志虽未独立定义，但建议补充上报
            logService.recordOperationLog(empNo, teacherName, "课堂分析", "STOP_TASK", taskId, "终止分析任务", 1);
            return Result.success("实时分析任务已结束！", null);
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? ((BusinessException) e).getMessage() : "系统内部异常";
            logService.recordOperationLog(empNo, teacherName, "课堂分析", "STOP_TASK", taskId, "终止分析任务失败：" + msg, 0);
            throw e;
        }
    }


    @GetMapping("/{taskId}/detail")
    public Result<AnalysisTaskDetailDTO> getTaskDetail(@PathVariable("taskId") Long taskId) {
        AnalysisTaskDetailDTO detailDTO = analysisTaskService.getTaskDetail(taskId);
        if (detailDTO == null) {
            return Result.error(404, "Task detail not found");
        }
        return Result.success(detailDTO);
    }
}
