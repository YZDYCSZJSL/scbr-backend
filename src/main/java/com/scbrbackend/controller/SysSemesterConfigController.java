package com.scbrbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.TeacherMapper;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.model.vo.SemesterConfigOptionVO;
import com.scbrbackend.service.SysSemesterConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/semester-config")
public class SysSemesterConfigController {

    @Autowired
    private SysSemesterConfigService sysSemesterConfigService;

    @Autowired
    private TeacherMapper teacherMapper;

    private void checkAdminRole(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException(401, "未授权的访问！");
        }
        String authToken = token.trim();
        if (authToken.startsWith("Bearer ")) {
            authToken = authToken.substring(7).trim();
        }
        if (!authToken.startsWith("mock_jwt_token_")) {
            throw new BusinessException(401, "未授权的访问！");
        }
        String empNo = authToken.substring("mock_jwt_token_".length());
        Teacher teacher = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, empNo));
        if (teacher == null || teacher.getRole() != 1) {
            throw new BusinessException(403, "无权限访问，仅限管理员操作！");
        }
    }

    /**
     * 学期配置下拉接口
     */
    @GetMapping("/options")
    public Result<List<SemesterConfigOptionVO>> getOptions(
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkAdminRole(token);
        return sysSemesterConfigService.getOptions();
    }
}
