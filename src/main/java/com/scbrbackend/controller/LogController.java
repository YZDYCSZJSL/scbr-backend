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

    private void checkAdminRole() {
        com.scbrbackend.common.context.CurrentUser user = com.scbrbackend.common.context.UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未授权的访问！");
        }
        if (user.getRole() == null || user.getRole() != 1) {
            throw new BusinessException(403, "无权限访问日志中心！");
        }
    }

    @GetMapping("/login/page")
    public Result<PageResult<LoginLogPageVO>> getLoginLogPage(
            LoginLogPageQueryDTO query,
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkAdminRole();
        PageResult<LoginLogPageVO> pageData = logService.getLoginLogPage(query);
        return Result.success("success", pageData);
    }

    @GetMapping("/operation/page")
    public Result<PageResult<OperationLogPageVO>> getOperationLogPage(
            OperationLogPageQueryDTO query,
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkAdminRole();
        PageResult<OperationLogPageVO> pageData = logService.getOperationLogPage(query);
        return Result.success("success", pageData);
    }

    @GetMapping("/task/page")
    public Result<PageResult<TaskLogPageVO>> getTaskLogPage(
            TaskLogPageQueryDTO query,
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkAdminRole();
        PageResult<TaskLogPageVO> pageData = logService.getTaskLogPage(query);
        return Result.success("success", pageData);
    }
}
