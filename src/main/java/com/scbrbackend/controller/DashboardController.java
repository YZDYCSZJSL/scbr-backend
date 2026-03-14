package com.scbrbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.TeacherMapper;
import com.scbrbackend.model.dto.dashboard.DashboardOverviewDTO;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final TeacherMapper teacherMapper;

    private Teacher getCurrentTeacher(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || !token.startsWith("mock_jwt_token_")) {
            throw new BusinessException(401, "登录状态已失效，请重新登录！");
        }
        String empNo = token.substring("mock_jwt_token_".length());

        Teacher currentTeacher = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, empNo));

        if (currentTeacher == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录！");
        }
        return currentTeacher;
    }

    @GetMapping("/overview")
    public Result<DashboardOverviewDTO> getOverview(
            @RequestParam(value = "days", defaultValue = "7") Integer days,
            HttpServletRequest request) {
        Teacher currentTeacher = getCurrentTeacher(request);
        DashboardOverviewDTO dto = dashboardService.getOverview(currentTeacher, days);
        return Result.success(dto);
    }
}
