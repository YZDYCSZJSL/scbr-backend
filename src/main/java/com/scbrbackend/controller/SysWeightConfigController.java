package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.SystemConfigDTO;
import com.scbrbackend.service.SysWeightConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.mapper.TeacherMapper;

@RestController
@RequestMapping("/api/v1/admin/config")
public class SysWeightConfigController {

    @Autowired
    private SysWeightConfigService sysWeightConfigService;

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
    public Result<PageResult<SystemConfigDTO>> getConfigPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return sysWeightConfigService.getConfigPage(page, size);
    }

    @PostMapping
    public Result<Object> addConfig(@RequestBody SystemConfigDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = sysWeightConfigService.addConfig(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "CREATE_CONFIG", requestDTO.getId(), "新增权重配置", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "CREATE_CONFIG", requestDTO.getId(), "新增权重配置失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "CREATE_CONFIG", requestDTO.getId(), "新增权重配置失败：" + msg, 0);
            throw e;
        }
    }

    @PutMapping
    public Result<Object> updateConfig(@RequestBody SystemConfigDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = sysWeightConfigService.updateConfig(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "UPDATE_CONFIG", requestDTO.getId(), "修改权重配置", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "UPDATE_CONFIG", requestDTO.getId(), "修改权重配置失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "UPDATE_CONFIG", requestDTO.getId(), "修改权重配置失败：" + msg, 0);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteConfig(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<Object> res = sysWeightConfigService.deleteConfig(id);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "DELETE_CONFIG", id, "删除权重配置", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "DELETE_CONFIG", id, "删除权重配置失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "DELETE_CONFIG", id, "删除权重配置失败：" + msg, 0);
            throw e;
        }
    }

    @PutMapping("/{id}/active")
    public Result<String> activateConfig(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<String> res = sysWeightConfigService.activateConfig(id);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "ACTIVATE_CONFIG", id, "激活权重配置", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "ACTIVATE_CONFIG", id, "激活权重配置失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "ACTIVATE_CONFIG", id, "激活权重配置失败：" + msg, 0);
            throw e;
        }
    }

    @PostMapping("/import")
    public Result<String> importConfig(@RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Teacher teacher = getCurrentTeacher(token);
        try {
            Result<String> res = sysWeightConfigService.importConfig(file);
            if (res.getCode() == 200) {
                Long bizId = res.getData() != null ? Long.parseLong(res.getData()) : null;
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "IMPORT_CONFIG", bizId, "导入权重配置", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "IMPORT_CONFIG", null, "导入权重配置失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(), "系统参数", "IMPORT_CONFIG", null, "导入权重配置失败：" + msg, 0);
            throw e;
        }
    }
}
