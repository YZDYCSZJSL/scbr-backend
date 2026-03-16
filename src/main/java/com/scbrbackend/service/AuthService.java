package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.LoginRequestDTO;
import com.scbrbackend.model.dto.LoginResponseDTO;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.mapper.TeacherMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import com.scbrbackend.model.entity.SysLoginLog;

/**
 * 认证 Service
 */
@Service
public class AuthService {

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private LogService logService;

    private void saveLoginLog(String empNo, String userName, int status, String message, HttpServletRequest request) {
        SysLoginLog log = new SysLoginLog();
        log.setEmpNo(empNo);
        log.setUserName(userName);
        log.setLoginStatus(status);
        log.setLoginMessage(message);
        if (request != null) {
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(request.getHeader("User-Agent"));
        }
        log.setCreatedAt(LocalDateTime.now());
        logService.recordLoginLog(log);
    }

    /**
     * 登录逻辑实现
     */
    public Result<LoginResponseDTO> login(LoginRequestDTO loginRequest, HttpServletRequest request) {
        // 1. 查询用户
        Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                .eq(Teacher::getEmpNo, loginRequest.getUsername()));

        // 2. 校验账号存在
        if (teacher == null) {
            saveLoginLog(loginRequest.getUsername(), null, 0, "账号不存在！", request);
            return Result.error(401, "账号不存在！");
        }

        // 3. 校验账号状态
        if (teacher.getStatus() == 0) {
            saveLoginLog(loginRequest.getUsername(), teacher.getName(), 0, "该账号已被禁用", request);
            return Result.error(403, "该账号已被禁用，请联系管理员！");
        }

        // 4. 校验密码 (前端传入明文，需与数据库MD5密文比对)
        String encryptedPass = DigestUtils.md5DigestAsHex(loginRequest.getPassword().getBytes());
        if (!teacher.getPassword().equals(encryptedPass)) {
            saveLoginLog(loginRequest.getUsername(), teacher.getName(), 0, "密码错误", request);
            return Result.error(401, "用户名或密码错误！");
        }

        // 5. 生成响应数据 (这里暂时模拟生成 Token)
        String mockToken = "mock_jwt_token_" + teacher.getEmpNo();

        LoginResponseDTO response = LoginResponseDTO.builder()
                .token(mockToken)
                .userInfo(LoginResponseDTO.UserInfo.builder()
                        .id(teacher.getId())
                        .empNo(teacher.getEmpNo())
                        .name(teacher.getName())
                        .role(teacher.getRole())
                        .department(teacher.getDepartment())
                        .build())
                .build();

        saveLoginLog(teacher.getEmpNo(), teacher.getName(), 1, "登录成功", request);
        return Result.success("登录成功", response);
    }
}
