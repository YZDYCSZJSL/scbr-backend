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



    @GetMapping("/overview")
    public Result<DashboardOverviewDTO> getOverview(
            @RequestParam(value = "days", defaultValue = "7") Integer days,
            HttpServletRequest request) {
        // 这里需要传递 Teacher 实体给 dashboardService，我们通过 userContext 转换或查询
        com.scbrbackend.common.context.CurrentUser currentUser = com.scbrbackend.common.context.UserContext.getCurrentUser();
        Teacher currentTeacher = new Teacher();
        currentTeacher.setId(currentUser.getId());
        currentTeacher.setEmpNo(currentUser.getEmpNo());
        currentTeacher.setName(currentUser.getName());
        currentTeacher.setRole(currentUser.getRole());
        currentTeacher.setDepartment(currentUser.getDepartment());
        DashboardOverviewDTO dto = dashboardService.getOverview(currentTeacher, days);
        return Result.success(dto);
    }
}
