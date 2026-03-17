package com.scbrbackend.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.context.CurrentUser;
import com.scbrbackend.common.context.UserContext;
import com.scbrbackend.mapper.TeacherMapper;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TeacherMapper teacherMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");

        if (!StringUtils.hasText(token)) {
            returnAuthError(response, 401, "未登录或登录已过期");
            return false;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else if (token.startsWith("mock_jwt_token_")) {
            returnAuthError(response, 401, "无效的登录令牌");
            return false;
        }

        try {
            // 获取工号
            String empNo = jwtUtil.getEmpNoFromToken(token);

            // 查询此用户状态
            Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, empNo));

            if (teacher == null) {
                returnAuthError(response, 401, "无效的登录令牌");
                return false;
            }

            if (teacher.getStatus() == 0) {
                returnAuthError(response, 403, "该账号已被禁用，请联系管理员");
                return false;
            }

            // 构建 currentUser 保存到线程变量
            CurrentUser currentUser = CurrentUser.builder()
                    .id(teacher.getId())
                    .empNo(teacher.getEmpNo())
                    .name(teacher.getName())
                    .role(teacher.getRole())
                    .department(teacher.getDepartment())
                    .build();

            UserContext.setCurrentUser(currentUser);
            return true;

        } catch (ExpiredJwtException e) {
            returnAuthError(response, 401, "登录已过期，请重新登录");
            return false;
        } catch (Exception e) {
            log.error("JWT校验异常: ", e);
            returnAuthError(response, 401, "无效的登录令牌");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理线程变量，防止内存泄漏
        UserContext.removeCurrentUser();
    }

    private void returnAuthError(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(200); // 统一项目返回风格
        Result<Void> result = Result.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
