package com.scbrbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.TeacherMapper;
import com.scbrbackend.model.dto.LoginLogPageQueryDTO;
import com.scbrbackend.model.dto.OperationLogPageQueryDTO;
import com.scbrbackend.model.dto.TaskLogPageQueryDTO;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.model.vo.LoginLogPageVO;
import com.scbrbackend.model.vo.OperationLogPageVO;
import com.scbrbackend.model.vo.TaskLogPageVO;
import com.scbrbackend.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/log")
public class LogController {

    @Autowired
    private LogService logService;

    @Autowired
    private TeacherMapper teacherMapper;

    private void checkAdminRole(String authorization) {
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

        if (teacher.getRole() == null || teacher.getRole() != 1) {
            throw new BusinessException(403, "无权限访问日志中心！");
        }
    }

    @GetMapping("/login/page")
    public Result<PageResult<LoginLogPageVO>> getLoginLogPage(
            LoginLogPageQueryDTO query,
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkAdminRole(token);
        PageResult<LoginLogPageVO> pageData = logService.getLoginLogPage(query);
        return Result.success("success", pageData);
    }

    @GetMapping("/operation/page")
    public Result<PageResult<OperationLogPageVO>> getOperationLogPage(
            OperationLogPageQueryDTO query,
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkAdminRole(token);
        PageResult<OperationLogPageVO> pageData = logService.getOperationLogPage(query);
        return Result.success("success", pageData);
    }

    @GetMapping("/task/page")
    public Result<PageResult<TaskLogPageVO>> getTaskLogPage(
            TaskLogPageQueryDTO query,
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkAdminRole(token);
        PageResult<TaskLogPageVO> pageData = logService.getTaskLogPage(query);
        return Result.success("success", pageData);
    }
}
