package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.TeacherRequestDTO;
import com.scbrbackend.model.dto.TeacherResponseDTO;
import com.scbrbackend.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/teacher")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;

    @GetMapping("/page")
    public Result<PageResult<TeacherResponseDTO>> getTeacherPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status) {
        return teacherService.getTeacherPage(page, size, keyword, role, status);
    }

    @PostMapping
    public Result<Object> addTeacher(@RequestBody TeacherRequestDTO requestDTO) {
        return teacherService.addTeacher(requestDTO);
    }

    @PutMapping
    public Result<Object> updateTeacher(@RequestBody TeacherRequestDTO requestDTO) {
        return teacherService.updateTeacher(requestDTO);
    }

    @PutMapping("/status/{id}")
    public Result<Object> changeStatus(@PathVariable Long id, @RequestBody TeacherRequestDTO requestDTO) {
        return teacherService.changeStatus(id, requestDTO.getStatus());
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteTeacher(@PathVariable Long id) {
        return teacherService.deleteTeacher(id);
    }
}
