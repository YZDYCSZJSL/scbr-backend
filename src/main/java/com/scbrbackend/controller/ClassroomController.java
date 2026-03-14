package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ClassroomRequestDTO;
import com.scbrbackend.model.dto.ClassroomResponseDTO;
import com.scbrbackend.service.ClassroomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/classroom")
public class ClassroomController {

    @Autowired
    private ClassroomService classroomService;

    @GetMapping("/page")
    public Result<PageResult<ClassroomResponseDTO>> getClassroomPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return classroomService.getClassroomPage(page, size, keyword, status);
    }

    @PostMapping
    public Result<Object> addClassroom(@RequestBody ClassroomRequestDTO requestDTO) {
        return classroomService.addClassroom(requestDTO);
    }

    @PutMapping
    public Result<Object> updateClassroom(@RequestBody ClassroomRequestDTO requestDTO) {
        return classroomService.updateClassroom(requestDTO);
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteClassroom(@PathVariable Long id) {
        return classroomService.deleteClassroom(id);
    }
}
