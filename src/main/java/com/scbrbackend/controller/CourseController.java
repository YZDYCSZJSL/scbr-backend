package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.CourseRequestDTO;
import com.scbrbackend.model.dto.CourseResponseDTO;
import com.scbrbackend.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/course")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @GetMapping("/page")
    public Result<PageResult<CourseResponseDTO>> getCoursePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return courseService.getCoursePage(page, size, keyword, status);
    }

    @PostMapping
    public Result<Object> addCourse(@RequestBody CourseRequestDTO requestDTO) {
        return courseService.addCourse(requestDTO);
    }

    @PutMapping
    public Result<Object> updateCourse(@RequestBody CourseRequestDTO requestDTO) {
        return courseService.updateCourse(requestDTO);
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteCourse(@PathVariable Long id) {
        return courseService.deleteCourse(id);
    }
}
