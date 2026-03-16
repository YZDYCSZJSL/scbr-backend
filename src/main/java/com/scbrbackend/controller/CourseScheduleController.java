package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ScheduleRequestDTO;
import com.scbrbackend.model.dto.ScheduleResponseDTO;
import com.scbrbackend.service.CourseScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.mapper.TeacherMapper;

@RestController
@RequestMapping("/api/v1/admin/schedule")
public class CourseScheduleController {

    @Autowired
    private CourseScheduleService courseScheduleService;

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private com.scbrbackend.service.LogService logService;

    private Teacher getCurrentTeacher(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            throw new BusinessException(401, "未授权的访问！");
        }
        String token = authorization.trim();
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
    public Result<PageResult<ScheduleResponseDTO>> getSchedulePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer status) {
        return courseScheduleService.getSchedulePage(page, size, teacherId, classroomId, courseId, status);
    }

    @PostMapping
    public Result<String> addSchedule(@RequestBody ScheduleRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        Result<String> res = courseScheduleService.saveOrUpdateSchedule(requestDTO);
        if (res.getCode() == 200) {
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "CREATE_SCHEDULE", requestDTO.getId(), "新增排课", 1);
        } else {
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "CREATE_SCHEDULE", null, "新增排课失败", 0);
        }
        return res;
    }

    @PutMapping
    public Result<String> updateSchedule(@RequestBody ScheduleRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<String> res = courseScheduleService.saveOrUpdateSchedule(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "UPDATE_SCHEDULE", requestDTO.getId(), "修改排课", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "UPDATE_SCHEDULE", requestDTO.getId(), "修改排课失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "UPDATE_SCHEDULE", requestDTO.getId(), "修改排课失败：" + msg, 0);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteSchedule(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = courseScheduleService.deleteSchedule(id);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "DELETE_SCHEDULE", id, "删除排课", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "DELETE_SCHEDULE", id, "删除排课失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "排课管理", "DELETE_SCHEDULE", id, "删除排课失败：" + msg, 0);
            throw e;
        }
    }

    @GetMapping("/analysis-list")
    public Result<List<ScheduleResponseDTO>> getAnalysisList(
            @RequestParam int streamType,
            jakarta.servlet.http.HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        // Remove Bearer prefix if exists
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return courseScheduleService.getAnalysisList(streamType, token);
    }
}
