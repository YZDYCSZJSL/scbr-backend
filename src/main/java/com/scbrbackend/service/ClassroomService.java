package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ClassroomRequestDTO;
import com.scbrbackend.model.dto.ClassroomResponseDTO;
import com.scbrbackend.model.entity.Classroom;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.mapper.ClassroomMapper;
import com.scbrbackend.mapper.CourseScheduleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassroomService extends ServiceImpl<ClassroomMapper, Classroom> {

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    public Result<PageResult<ClassroomResponseDTO>> getClassroomPage(int page, int size, String keyword,
            Integer status) {
        Page<Classroom> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Classroom> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(i -> i.like(Classroom::getRoomNo, keyword)
                    .or().like(Classroom::getName, keyword)
                    .or().like(Classroom::getLocation, keyword));
        }
        if (status != null) {
            wrapper.eq(Classroom::getStatus, status);
        }

        wrapper.orderByDesc(Classroom::getCreatedAt);

        Page<Classroom> classroomPage = this.page(pageParam, wrapper);

        List<ClassroomResponseDTO> dtoList = classroomPage.getRecords().stream().map(classroom -> {
            ClassroomResponseDTO dto = new ClassroomResponseDTO();
            BeanUtils.copyProperties(classroom, dto);
            dto.setRoomNo(classroom.getRoomNo());
            return dto;
        }).collect(Collectors.toList());

        PageResult<ClassroomResponseDTO> result = new PageResult<>();
        result.setTotal(classroomPage.getTotal());
        result.setPage(classroomPage.getCurrent());
        result.setSize(classroomPage.getSize());
        result.setRecords(dtoList);

        return Result.success("success", result);
    }

    public Result<Object> addClassroom(ClassroomRequestDTO requestDTO) {
        long count = this.count(new LambdaQueryWrapper<Classroom>().eq(Classroom::getRoomNo, requestDTO.getRoomNo()));
        if (count > 0) {
            return Result.error(400, "教室编号已存在！");
        }

        Classroom classroom = new Classroom();
        BeanUtils.copyProperties(requestDTO, classroom);
        classroom.setCreatedAt(LocalDateTime.now());
        classroom.setUpdatedAt(LocalDateTime.now());

        this.save(classroom);
        requestDTO.setId(classroom.getId());
        return Result.success("保存成功！", null);
    }

    public Result<Object> updateClassroom(ClassroomRequestDTO requestDTO) {
        Classroom classroom = this.getById(requestDTO.getId());
        if (classroom == null)
            return Result.error(404, "教室不存在");

        if (StringUtils.hasText(requestDTO.getRoomNo()) && !requestDTO.getRoomNo().equals(classroom.getRoomNo())) {
            long count = this
                    .count(new LambdaQueryWrapper<Classroom>().eq(Classroom::getRoomNo, requestDTO.getRoomNo()));
            if (count > 0)
                return Result.error(400, "教室编号已存在！");
            classroom.setRoomNo(requestDTO.getRoomNo());
        }

        if (StringUtils.hasText(requestDTO.getName()))
            classroom.setName(requestDTO.getName());
        if (StringUtils.hasText(requestDTO.getLocation()))
            classroom.setLocation(requestDTO.getLocation());
        if (requestDTO.getCapacity() != null)
            classroom.setCapacity(requestDTO.getCapacity());
        if (requestDTO.getStatus() != null)
            classroom.setStatus(requestDTO.getStatus());

        classroom.setUpdatedAt(LocalDateTime.now());
        this.updateById(classroom);

        return Result.success("保存成功！", null);
    }

    public Result<Object> deleteClassroom(Long id) {
        long count = courseScheduleMapper
                .selectCount(new LambdaQueryWrapper<CourseSchedule>().eq(CourseSchedule::getClassroomId, id));
        if (count > 0) {
            return Result.error(500, "删除失败，该教室正在被排课占用！");
        }

        this.removeById(id);
        return Result.success("删除成功！", null);
    }
}
