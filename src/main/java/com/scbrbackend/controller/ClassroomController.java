package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ClassroomRequestDTO;
import com.scbrbackend.model.dto.ClassroomResponseDTO;
import com.scbrbackend.service.ClassroomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.mapper.TeacherMapper;

@RestController
@RequestMapping("/api/v1/admin/classroom")
public class ClassroomController {

    @Autowired
    private ClassroomService classroomService;

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
    public Result<PageResult<ClassroomResponseDTO>> getClassroomPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return classroomService.getClassroomPage(page, size, keyword, status);
    }

    @PostMapping
    public Result<Object> addClassroom(@RequestBody ClassroomRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = classroomService.addClassroom(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "CREATE_CLASSROOM", requestDTO.getId(), "新增教室", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "CREATE_CLASSROOM", requestDTO.getId(), "新增教室失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "CREATE_CLASSROOM", requestDTO.getId(), "新增教室失败：" + msg, 0);
            throw e;
        }
    }

    @PutMapping
    public Result<Object> updateClassroom(@RequestBody ClassroomRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = classroomService.updateClassroom(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "UPDATE_CLASSROOM", requestDTO.getId(), "修改教室", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "UPDATE_CLASSROOM", requestDTO.getId(), "修改教室失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "UPDATE_CLASSROOM", requestDTO.getId(), "修改教室失败：" + msg, 0);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteClassroom(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = classroomService.deleteClassroom(id);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "DELETE_CLASSROOM", id, "删除教室", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "DELETE_CLASSROOM", id, "删除教室失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教室管理", "DELETE_CLASSROOM", id, "删除教室失败：" + msg, 0);
            throw e;
        }
    }
}
