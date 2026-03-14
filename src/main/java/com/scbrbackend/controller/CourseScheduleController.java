package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ScheduleRequestDTO;
import com.scbrbackend.model.dto.ScheduleResponseDTO;
import com.scbrbackend.service.CourseScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/schedule")
public class CourseScheduleController {

    @Autowired
    private CourseScheduleService courseScheduleService;

    @GetMapping("/page")
    public Result<PageResult<ScheduleResponseDTO>> getSchedulePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer status) {
        return courseScheduleService.getSchedulePage(page, size, teacherId, classroomId, courseId, status);
    }

    @PostMapping
    public Result<String> addSchedule(@RequestBody ScheduleRequestDTO requestDTO) {
        return courseScheduleService.saveOrUpdateSchedule(requestDTO);
    }

    @PutMapping
    public Result<String> updateSchedule(@RequestBody ScheduleRequestDTO requestDTO) {
        return courseScheduleService.saveOrUpdateSchedule(requestDTO);
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteSchedule(@PathVariable Long id) {
        return courseScheduleService.deleteSchedule(id);
    }

    @GetMapping("/analysis-list")
    public Result<List<ScheduleResponseDTO>> getAnalysisList(
            @RequestParam int streamType,
            jakarta.servlet.http.HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        // Remove Bearer prefix if exists
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return courseScheduleService.getAnalysisList(streamType, token);
    }
}
