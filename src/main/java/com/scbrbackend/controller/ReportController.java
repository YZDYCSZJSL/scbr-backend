package com.scbrbackend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ReportExportDTO;
import com.scbrbackend.model.dto.ReportPageQueryDTO;
import com.scbrbackend.model.vo.ReportDetailVO;
import com.scbrbackend.model.vo.ReportPageVO;
import com.scbrbackend.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/report")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final com.scbrbackend.mapper.TeacherMapper teacherMapper;

    private Long getFilteredTeacherId(jakarta.servlet.http.HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || !token.startsWith("mock_jwt_token_")) {
            throw new com.scbrbackend.common.exception.BusinessException(401, "未授权的访问！");
        }
        String empNo = token.substring("mock_jwt_token_".length());

        com.scbrbackend.model.entity.Teacher currentTeacher = teacherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.scbrbackend.model.entity.Teacher>()
                        .eq(com.scbrbackend.model.entity.Teacher::getEmpNo, empNo));

        if (currentTeacher == null) {
            throw new com.scbrbackend.common.exception.BusinessException(401, "无效的用户令牌！");
        }

        // 普通老师：Role == 0, 超级管理员: Role == 1
        if (currentTeacher.getRole() != null && currentTeacher.getRole() == 1) {
            return null; // 不加条件过滤，全部可看
        }
        return currentTeacher.getId(); // 普通老师只能查看自己的
    }

    @GetMapping("/page")
    public Result<PageResult<ReportPageVO>> getPage(ReportPageQueryDTO query,
            jakarta.servlet.http.HttpServletRequest request) {
        Long currentTeacherId = getFilteredTeacherId(request);
        IPage<ReportPageVO> pageResult = reportService.getPage(query, currentTeacherId);
        return Result.success(PageResult.from(pageResult));
    }

    @PostMapping("/export")
    public void export(@RequestBody ReportExportDTO dto, HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest request) {
        Long currentTeacherId = getFilteredTeacherId(request);
        reportService.exportByIds(dto, response, currentTeacherId);
    }

    @GetMapping("/{id}/detail")
    public Result<ReportDetailVO> getDetail(@PathVariable("id") Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        Long currentTeacherId = getFilteredTeacherId(request);
        ReportDetailVO detail = reportService.getDetailById(id, currentTeacherId);
        return Result.success(detail);
    }
}
