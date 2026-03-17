package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.TeacherRequestDTO;
import com.scbrbackend.model.dto.TeacherResponseDTO;
import com.scbrbackend.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.scbrbackend.common.exception.BusinessException;


@RestController
@RequestMapping("/api/v1/admin/teacher")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;



    @Autowired
    private com.scbrbackend.service.LogService logService;



    @GetMapping("/page")
    public Result<PageResult<TeacherResponseDTO>> getTeacherPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status) {
        return teacherService.getTeacherPage(page, size, keyword, role, status);
    }

    @PostMapping
    public Result<Object> addTeacher(@RequestBody TeacherRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        try {
            Result<Object> res = teacherService.addTeacher(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "CREATE_TEACHER", requestDTO.getId(), "新增教师", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "CREATE_TEACHER", requestDTO.getId(), "新增教师失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "CREATE_TEACHER", requestDTO.getId(), "新增教师失败：" + msg, 0);
            throw e;
        }
    }

    @PutMapping
    public Result<Object> updateTeacher(@RequestBody TeacherRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        try {
            Result<Object> res = teacherService.updateTeacher(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "UPDATE_TEACHER", requestDTO.getId(), "修改教师", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "UPDATE_TEACHER", requestDTO.getId(), "修改教师失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "UPDATE_TEACHER", requestDTO.getId(), "修改教师失败：" + msg, 0);
            throw e;
        }
    }

    @PutMapping("/status/{id}")
    public Result<Object> changeStatus(@PathVariable Long id, @RequestBody TeacherRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        try {
            Result<Object> res = teacherService.changeStatus(id, requestDTO.getStatus());
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "UPDATE_TEACHER", id, "修改教师状态", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "UPDATE_TEACHER", id, "修改教师状态失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "UPDATE_TEACHER", id, "修改教师状态失败：" + msg, 0);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteTeacher(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        try {
            Result<Object> res = teacherService.deleteTeacher(id);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "DELETE_TEACHER", id, "删除教师", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "DELETE_TEACHER", id, "删除教师失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "教师管理", "DELETE_TEACHER", id, "删除教师失败：" + msg, 0);
            throw e;
        }
    }
}
