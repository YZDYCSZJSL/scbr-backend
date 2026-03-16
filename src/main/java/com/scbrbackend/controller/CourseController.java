package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.CourseRequestDTO;
import com.scbrbackend.model.dto.CourseResponseDTO;
import com.scbrbackend.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.mapper.TeacherMapper;

@RestController
@RequestMapping("/api/v1/admin/course")
public class CourseController {

    @Autowired
    private CourseService courseService;

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
    public Result<PageResult<CourseResponseDTO>> getCoursePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return courseService.getCoursePage(page, size, keyword, status);
    }

    @PostMapping
    public Result<Object> addCourse(@RequestBody CourseRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = courseService.addCourse(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "CREATE_COURSE", requestDTO.getId(), "新增课程", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "CREATE_COURSE", requestDTO.getId(), "新增课程失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "CREATE_COURSE", requestDTO.getId(), "新增课程失败：" + msg, 0);
            throw e;
        }
    }

    @PutMapping
    public Result<Object> updateCourse(@RequestBody CourseRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = courseService.updateCourse(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "UPDATE_COURSE", requestDTO.getId(), "修改课程", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "UPDATE_COURSE", requestDTO.getId(), "修改课程失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "UPDATE_COURSE", requestDTO.getId(), "修改课程失败：" + msg, 0);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteCourse(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = courseService.deleteCourse(id);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "DELETE_COURSE", id, "删除课程", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "DELETE_COURSE", id, "删除课程失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "课程管理", "DELETE_COURSE", id, "删除课程失败：" + msg, 0);
            throw e;
        }
    }
}
