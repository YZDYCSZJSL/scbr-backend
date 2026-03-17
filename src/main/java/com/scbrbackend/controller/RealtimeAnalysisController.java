package com.scbrbackend.controller;

import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.RealtimeAnalysisStartDTO;
import com.scbrbackend.model.dto.RealtimeStopDTO;
import com.scbrbackend.model.vo.RealtimeAnalysisStartVO;
import com.scbrbackend.model.vo.RealtimeFrameResultVO;
import com.scbrbackend.model.vo.RealtimeTaskStatusVO;
import com.scbrbackend.model.vo.RealtimeTaskStopVO;
import com.scbrbackend.service.RealtimeAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/analysis/realtime")
public class RealtimeAnalysisController {

    @Autowired
    private RealtimeAnalysisService realtimeAnalysisService;

    // Helper method for token extraction
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return token;
    }

    @PostMapping("/start")
    public Result<RealtimeAnalysisStartVO> startRealtimeAnalysis(@RequestBody RealtimeAnalysisStartDTO dto, HttpServletRequest request) {
        RealtimeAnalysisStartVO vo = realtimeAnalysisService.createRealtimeTask(dto.getScheduleId(), extractToken(request));
        return Result.success("启动实时分析成功", vo);
    }

    @PostMapping("/{taskId}/frame")
    public Result<RealtimeFrameResultVO> processFrame(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "frame", required = false) MultipartFile frame,
            @RequestParam("frameTime") Integer frameTime,
            @RequestParam(value = "snapshotType", defaultValue = "normal") String snapshotType,
            HttpServletRequest request) {

        RealtimeFrameResultVO vo = realtimeAnalysisService.processRealtimeFrame(taskId, frame, frameTime, snapshotType);
        return Result.success("实时帧分析成功", vo);
    }

    @PostMapping("/{taskId}/stop")
    public Result<RealtimeTaskStopVO> stopRealtimeAnalysis(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) RealtimeStopDTO dto,
            HttpServletRequest request) {

        RealtimeTaskStopVO vo = realtimeAnalysisService.stopRealtimeTask(taskId, extractToken(request));
        return Result.success("停止实时分析成功", vo);
    }

    @GetMapping("/{taskId}/status")
    public Result<RealtimeTaskStatusVO> getTaskStatus(@PathVariable("taskId") Long taskId) {
        RealtimeTaskStatusVO vo = realtimeAnalysisService.getRealtimeTaskStatus(taskId);
        return Result.success("success", vo);
    }
}
