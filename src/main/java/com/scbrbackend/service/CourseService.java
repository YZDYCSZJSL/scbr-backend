package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.CourseRequestDTO;
import com.scbrbackend.model.dto.CourseResponseDTO;
import com.scbrbackend.model.entity.Course;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.mapper.CourseMapper;
import com.scbrbackend.mapper.CourseScheduleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseService extends ServiceImpl<CourseMapper, Course> {

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    public Result<PageResult<CourseResponseDTO>> getCoursePage(int page, int size, String keyword, Integer status) {
        Page<Course> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(i -> i.like(Course::getCourseNo, keyword)
                    .or().like(Course::getName, keyword)
                    .or().like(Course::getDepartment, keyword));
        }
        if (status != null) {
            wrapper.eq(Course::getStatus, status);
        }

        wrapper.orderByDesc(Course::getCreatedAt);

        Page<Course> coursePage = this.page(pageParam, wrapper);

        List<CourseResponseDTO> dtoList = coursePage.getRecords().stream().map(course -> {
            CourseResponseDTO dto = new CourseResponseDTO();
            BeanUtils.copyProperties(course, dto);
            dto.setCourseNo(course.getCourseNo());
            return dto;
        }).collect(Collectors.toList());

        PageResult<CourseResponseDTO> result = new PageResult<>();
        result.setTotal(coursePage.getTotal());
        result.setPage(coursePage.getCurrent());
        result.setSize(coursePage.getSize());
        result.setRecords(dtoList);

        return Result.success("success", result);
    }

    public Result<Object> addCourse(CourseRequestDTO requestDTO) {
        long count = this.count(new LambdaQueryWrapper<Course>().eq(Course::getCourseNo, requestDTO.getCourseNo()));
        if (count > 0) {
            return Result.error(400, "课程编号已存在！");
        }

        Course course = new Course();
        BeanUtils.copyProperties(requestDTO, course);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());

        this.save(course);
        return Result.success("新增课程成功！", null);
    }

    public Result<Object> updateCourse(CourseRequestDTO requestDTO) {
        Course course = this.getById(requestDTO.getId());
        if (course == null)
            return Result.error(404, "课程不存在");

        if (StringUtils.hasText(requestDTO.getCourseNo()) && !requestDTO.getCourseNo().equals(course.getCourseNo())) {
            long count = this.count(new LambdaQueryWrapper<Course>().eq(Course::getCourseNo, requestDTO.getCourseNo()));
            if (count > 0)
                return Result.error(400, "课程编号已存在！");
            course.setCourseNo(requestDTO.getCourseNo());
        }

        if (StringUtils.hasText(requestDTO.getName()))
            course.setName(requestDTO.getName());
        if (StringUtils.hasText(requestDTO.getDepartment()))
            course.setDepartment(requestDTO.getDepartment());
        if (requestDTO.getHours() != null)
            course.setHours(requestDTO.getHours());
        if (requestDTO.getStatus() != null)
            course.setStatus(requestDTO.getStatus());

        course.setUpdatedAt(LocalDateTime.now());
        this.updateById(course);

        return Result.success("保存成功！", null);
    }

    public Result<Object> deleteCourse(Long id) {
        long count = courseScheduleMapper
                .selectCount(new LambdaQueryWrapper<CourseSchedule>().eq(CourseSchedule::getCourseId, id));
        if (count > 0) {
            return Result.error(500, "删除失败，该课程已存在排课安排！");
        }

        this.removeById(id);
        return Result.success("删除成功！", null);
    }
}
