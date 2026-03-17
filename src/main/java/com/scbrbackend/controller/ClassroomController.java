package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ClassroomRequestDTO;
import com.scbrbackend.model.dto.ClassroomResponseDTO;
import com.scbrbackend.service.ClassroomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.scbrbackend.common.exception.BusinessException;


@RestController
@RequestMapping("/api/v1/admin/classroom")
public class ClassroomController {

    @Autowired
    private ClassroomService classroomService;



    @Autowired
    private com.scbrbackend.service.LogService logService;



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
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
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
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
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
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
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
