package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.AnalysisDetailMapper;
import com.scbrbackend.mapper.AnalysisTaskLogMapper;
import com.scbrbackend.mapper.AnalysisTaskMapper;
import com.scbrbackend.mapper.CourseScheduleMapper;
import com.scbrbackend.mapper.TeacherMapper;
import com.scbrbackend.model.dto.ModelFileCallbackDTO;
import com.scbrbackend.model.entity.AnalysisDetail;
import com.scbrbackend.model.entity.AnalysisTask;
import com.scbrbackend.model.entity.AnalysisTaskLog;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.model.vo.RealtimeAnalysisStartVO;
import com.scbrbackend.model.vo.RealtimeBehaviorVO;
import com.scbrbackend.model.vo.RealtimeFrameResultVO;
import com.scbrbackend.model.vo.RealtimeTaskStatusVO;
import com.scbrbackend.model.vo.RealtimeTaskStopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RealtimeAnalysisService {

    @Autowired
    private AnalysisTaskMapper analysisTaskMapper;

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private AnalysisTaskLogMapper analysisTaskLogMapper;

    @Autowired
    private AnalysisDetailMapper analysisDetailMapper;

    @Autowired
    private ModelCallbackService modelCallbackService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogService logService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Teacher getCurrentTeacher(String token) {
        if (token == null || !token.startsWith("mock_jwt_token_")) {
            throw new BusinessException(401, "未授权的访问！");
        }
        String empNo = token.substring("mock_jwt_token_".length());
        Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, empNo));
        if (teacher == null) {
            throw new BusinessException(401, "无效的用户令牌！");
        }
        return teacher;
    }

    @Transactional
    public RealtimeAnalysisStartVO createRealtimeTask(Long scheduleId, String token) {
        Teacher currentTeacher = getCurrentTeacher(token);

        CourseSchedule schedule = courseScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(400, "排课不存在或不可用");
        }

        AnalysisTask task = new AnalysisTask();
        task.setTeacherId(currentTeacher.getId());
        task.setClassroomId(schedule.getClassroomId());
        task.setScheduleId(scheduleId);
        task.setFileId(null);
        task.setMediaType(3); // 3-实时流
        task.setStatus(1); // 1-分析中
        task.setAttendanceCount(0);
        task.setTotalScore(BigDecimal.ZERO);
        task.setRetryCount(0);
        task.setStartTime(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.insert(task);

        // CREATED Log
        AnalysisTaskLog createdLog = new AnalysisTaskLog();
        createdLog.setTaskId(task.getId());
        createdLog.setStage("CREATED");
        createdLog.setStatus(1);
        createdLog.setMessage("实时分析任务创建成功");
        createdLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(createdLog);

        // RUNNING Log
        AnalysisTaskLog runningLog = new AnalysisTaskLog();
        runningLog.setTaskId(task.getId());
        runningLog.setStage("RUNNING");
        runningLog.setStatus(1);
        runningLog.setMessage("实时任务已开始分析");
        runningLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(runningLog);

        logService.recordOperationLog(currentTeacher.getEmpNo(), currentTeacher.getName(), "课堂分析", "START_REALTIME_ANALYSIS", task.getId(), "教师启动实时分析任务", 1);

        RealtimeAnalysisStartVO vo = new RealtimeAnalysisStartVO();
        vo.setTaskId(task.getId());
        vo.setScheduleId(scheduleId);
        vo.setMediaType(task.getMediaType());
        vo.setStatus(task.getStatus());
        vo.setStartTime(task.getStartTime() != null ? task.getStartTime().format(FORMATTER) : null);
        return vo;
    }

    @Transactional
    public RealtimeFrameResultVO processRealtimeFrame(Long taskId, MultipartFile frame, Integer frameTime, String type) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() != 1) {
            throw new BusinessException(400, "任务不存在或任务状态不是分析中");
        }
        if (task.getMediaType() != 3) {
            throw new BusinessException(400, "该任务不是实时分析任务");
        }
        
        // Log MODEL_ACCEPTED occasionally to avoid spam
        if (frameTime != null && frameTime % 30 == 0) {
            AnalysisTaskLog acceptedLog = new AnalysisTaskLog();
            acceptedLog.setTaskId(taskId);
            acceptedLog.setStage("MODEL_ACCEPTED");
            acceptedLog.setStatus(1);
            acceptedLog.setMessage("模型服务已受理实时帧(时间: " + frameTime + "s)");
            acceptedLog.setCreatedAt(LocalDateTime.now());
            analysisTaskLogMapper.insert(acceptedLog);
        }

        // Mock Model Process & Call Result
        List<RealtimeBehaviorVO> mockedBehaviors = callModelForSingleFrame(frame, frameTime);

        // Convert mocked results
        List<ModelFileCallbackDTO.Detail> callbackDetails = new ArrayList<>();
        int maxCount = 0;

        for (RealtimeBehaviorVO b : mockedBehaviors) {
            AnalysisDetail detail = new AnalysisDetail();
            detail.setTaskId(taskId);
            
            // Record type: 1-趋势聚合, 2-违规抓拍
            if (b.getBehaviorType().equals("玩手机") || b.getBehaviorType().equals("趴桌")) {
                detail.setRecordType(2); // violation
                detail.setSnapshotUrl(b.getSnapshotUrl());
            } else {
                detail.setRecordType(1); // trend
            }

            detail.setFrameTime(frameTime != null ? frameTime : 0);
            detail.setBehaviorType(b.getBehaviorType());
            detail.setCount(b.getCount() != null ? b.getCount() : 0);
            if (b.getBoundingBoxes() != null) {
                try {
                    detail.setBoundingBoxes(objectMapper.writeValueAsString(b.getBoundingBoxes()));
                } catch (Exception e) {}
            }
            analysisDetailMapper.insert(detail);

            ModelFileCallbackDTO.Detail cd = new ModelFileCallbackDTO.Detail();
            cd.setBehaviorType(b.getBehaviorType());
            cd.setCount(b.getCount());
            cd.setFrameTime(frameTime);
            callbackDetails.add(cd);

            if (b.getBehaviorType().equals("正常听课")) {
                maxCount = Math.max(maxCount, b.getCount()); // simple mock attendance logic for realtime 
            }
        }

        // Update attendance (incremental or max)
        task.setAttendanceCount(Math.max(task.getAttendanceCount(), maxCount));
        
        // Temporarily calculate current score
        double currentScore = modelCallbackService.calculateScore(task.getScheduleId(), task.getAttendanceCount(), callbackDetails);
        
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);

        if (frameTime != null && frameTime % 60 == 0) {
            AnalysisTaskLog waitLog = new AnalysisTaskLog();
            waitLog.setTaskId(taskId);
            waitLog.setStage("WAITING_CALLBACK");
            waitLog.setStatus(1);
            waitLog.setMessage("实时流持续处理中...");
            waitLog.setCreatedAt(LocalDateTime.now());
            analysisTaskLogMapper.insert(waitLog);
        }

        RealtimeFrameResultVO vo = new RealtimeFrameResultVO();
        vo.setTaskId(taskId);
        vo.setStatus(task.getStatus());
        vo.setFrameTime(frameTime);
        vo.setAttendanceCount(task.getAttendanceCount());
        vo.setCurrentScore(currentScore);
        vo.setBehaviors(mockedBehaviors);
        return vo;
    }

    @Transactional
    public RealtimeTaskStopVO stopRealtimeTask(Long taskId, String token) {
        Teacher currentTeacher = getCurrentTeacher(token);
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null || task.getMediaType() != 3) {
            throw new BusinessException(400, "实时任务不存在或类型不匹配");
        }

        if (task.getStatus() == 2) { // Allow idempotent stop
            RealtimeTaskStopVO vo = new RealtimeTaskStopVO();
            vo.setTaskId(taskId);
            vo.setStatus(task.getStatus());
            vo.setAttendanceCount(task.getAttendanceCount());
            vo.setTotalScore(task.getTotalScore() != null ? task.getTotalScore().doubleValue() : 0.0);
            vo.setStartTime(task.getStartTime() != null ? task.getStartTime().format(FORMATTER) : null);
            vo.setFinishTime(task.getFinishTime() != null ? task.getFinishTime().format(FORMATTER) : null);
            return vo;
        }

        // Aggregate to calculate final score
        List<AnalysisDetail> details = analysisDetailMapper.selectList(
            new LambdaQueryWrapper<AnalysisDetail>()
                .eq(AnalysisDetail::getTaskId, taskId)
                .in(AnalysisDetail::getRecordType, 1, 2)
        );

        List<ModelFileCallbackDTO.Detail> callbackList = new ArrayList<>();
        for (AnalysisDetail d : details) {
            ModelFileCallbackDTO.Detail cd = new ModelFileCallbackDTO.Detail();
            cd.setBehaviorType(d.getBehaviorType());
            cd.setCount(d.getCount());
            cd.setFrameTime(d.getFrameTime());
            callbackList.add(cd);
        }

        double finalScore = modelCallbackService.calculateScore(task.getScheduleId(), task.getAttendanceCount(), callbackList);

        task.setStatus(2);
        task.setFinishTime(LocalDateTime.now());
        task.setTotalScore(new BigDecimal(String.valueOf(finalScore)));
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);

        AnalysisTaskLog stopLog = new AnalysisTaskLog();
        stopLog.setTaskId(taskId);
        stopLog.setStage("STOPPED");
        stopLog.setStatus(1);
        stopLog.setMessage("手动停止实时分析");
        stopLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(stopLog);

        AnalysisTaskLog finishLog = new AnalysisTaskLog();
        finishLog.setTaskId(taskId);
        finishLog.setStage("FINISHED");
        finishLog.setStatus(1);
        finishLog.setMessage("实时任务执行完成，结果已固化");
        finishLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(finishLog);
        
        logService.recordOperationLog(currentTeacher.getEmpNo(), currentTeacher.getName(), "课堂分析", "STOP_REALTIME_ANALYSIS", task.getId(), "教师停止实时分析", 1);


        RealtimeTaskStopVO vo = new RealtimeTaskStopVO();
        vo.setTaskId(taskId);
        vo.setStatus(task.getStatus());
        vo.setAttendanceCount(task.getAttendanceCount());
        vo.setTotalScore(finalScore);
        vo.setStartTime(task.getStartTime() != null ? task.getStartTime().format(FORMATTER) : null);
        vo.setFinishTime(task.getFinishTime() != null ? task.getFinishTime().format(FORMATTER) : null);
        return vo;
    }

    public RealtimeTaskStatusVO getRealtimeTaskStatus(Long taskId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(400, "任务不存在");
        }
        RealtimeTaskStatusVO vo = new RealtimeTaskStatusVO();
        vo.setTaskId(task.getId());
        vo.setMediaType(task.getMediaType());
        vo.setStatus(task.getStatus());
        vo.setAttendanceCount(task.getAttendanceCount());
        vo.setTotalScore(task.getTotalScore() != null ? task.getTotalScore().doubleValue() : 0.0);
        vo.setStartTime(task.getStartTime() != null ? task.getStartTime().format(FORMATTER) : null);
        vo.setFinishTime(task.getFinishTime() != null ? task.getFinishTime().format(FORMATTER) : null);
        vo.setFailReason(task.getFailReason());
        return vo;
    }

    /**
     * Mock AI endpoint response
     */
    private List<RealtimeBehaviorVO> callModelForSingleFrame(MultipartFile frame, Integer frameTime) {
        List<RealtimeBehaviorVO> list = new ArrayList<>();
        
        RealtimeBehaviorVO normal = new RealtimeBehaviorVO();
        normal.setBehaviorType("正常听课");
        normal.setCount(28);
        List<List<Double>> box1 = new ArrayList<>();
        List<Double> c1 = new ArrayList<>();
        c1.add(80.0); c1.add(100.0); c1.add(180.0); c1.add(260.0);
        box1.add(c1);
        normal.setBoundingBoxes(box1);
        list.add(normal);

        // Sporadically add violation
        if (frameTime != null && frameTime % 15 == 0) {
            RealtimeBehaviorVO violation = new RealtimeBehaviorVO();
            violation.setBehaviorType("玩手机");
            violation.setCount(2);
            List<List<Double>> box2 = new ArrayList<>();
            List<Double> c2 = new ArrayList<>();
            c2.add(220.0); c2.add(140.0); c2.add(300.0); c2.add(300.0);
            box2.add(c2);
            violation.setBoundingBoxes(box2);
            violation.setSnapshotUrl("https://oss.example.com/snapshot/mock_phone_violation.jpg");
            list.add(violation);
        }
        return list;
    }

}
