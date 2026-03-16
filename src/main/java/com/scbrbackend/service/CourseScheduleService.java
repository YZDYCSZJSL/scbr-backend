package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.model.dto.ScheduleRequestDTO;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.mapper.CourseScheduleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import com.scbrbackend.common.Result.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scbrbackend.model.dto.ScheduleResponseDTO;
import com.scbrbackend.model.entity.Course;
import com.scbrbackend.model.entity.Classroom;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.mapper.CourseMapper;
import com.scbrbackend.mapper.ClassroomMapper;
import com.scbrbackend.mapper.TeacherMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class CourseScheduleService extends ServiceImpl<CourseScheduleMapper, CourseSchedule> {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ClassroomMapper classroomMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    public Result<String> saveOrUpdateSchedule(ScheduleRequestDTO dto) {
        // 0.1 状态锁死（查自己）：仅允许修改 status = 0（未开始）的排课
        if (dto.getId() != null) {
            CourseSchedule existSchedule = this.getById(dto.getId());
            if (existSchedule != null && existSchedule.getStatus() != 0) {
                throw new BusinessException(500, "操作失败：已开始或已结束的课程无法修改！");
            }
        }

        // 0.2 未来时间强制性：新 startTime 必须严格大于当前服务器系统时间
        if (dto.getStartTime() == null || !dto.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(500, "操作失败：排课的开始时间必须设定在未来！");
        }

        // 1. 防挤占检测：同一教室同时段不能有两个课程
        int classroomConflictCount = this.baseMapper.checkClassroomConflict(
                dto.getClassroomId(), dto.getStartTime(), dto.getEndTime(), dto.getId());
        if (classroomConflictCount > 0) {
            throw new BusinessException(500, "排课冲突：该教室在指定时间段内已被占用！");
        }

        // 2. 防分身检测：同一教师同时段不能有两节课
        int teacherConflictCount = this.baseMapper.checkTeacherConflict(
                dto.getTeacherId(), dto.getStartTime(), dto.getEndTime(), dto.getId());
        if (teacherConflictCount > 0) {
            throw new BusinessException(500, "排课冲突：当前选定时段该教师已安排其他课程，请重新规划！");
        }

        // 3. 落库
        CourseSchedule schedule = new CourseSchedule();
        BeanUtils.copyProperties(dto, schedule);
        // 新增时默认状态为0(未开始)
        if (schedule.getId() == null && schedule.getStatus() == null) {
            schedule.setStatus(0);
        }
        if (schedule.getCreatedAt() == null) {
            schedule.setCreatedAt(LocalDateTime.now());
        }
        schedule.setUpdatedAt(LocalDateTime.now());

        this.saveOrUpdate(schedule);
        dto.setId(schedule.getId());
        return Result.success("保存成功！数据无冲突。", null);
    }

    public Result<PageResult<ScheduleResponseDTO>> getSchedulePage(int page, int size, Long teacherId, Long classroomId,
            Long courseId, Integer status) {
        Page<CourseSchedule> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CourseSchedule> wrapper = new LambdaQueryWrapper<>();

        if (teacherId != null)
            wrapper.eq(CourseSchedule::getTeacherId, teacherId);
        if (classroomId != null)
            wrapper.eq(CourseSchedule::getClassroomId, classroomId);
        if (courseId != null)
            wrapper.eq(CourseSchedule::getCourseId, courseId);
        if (status != null)
            wrapper.eq(CourseSchedule::getStatus, status);

        wrapper.orderByDesc(CourseSchedule::getCreatedAt);
        Page<CourseSchedule> schedulePage = this.page(pageParam, wrapper);

        List<ScheduleResponseDTO> dtoList = schedulePage.getRecords().stream().map(schedule -> {
            ScheduleResponseDTO dto = new ScheduleResponseDTO();
            BeanUtils.copyProperties(schedule, dto);

            // 从数据库获取实际关联名称
            if (schedule.getCourseId() != null) {
                Course course = courseMapper.selectById(schedule.getCourseId());
                dto.setCourseName(course != null ? course.getName() : "未知课程");
            }
            if (schedule.getClassroomId() != null) {
                Classroom classroom = classroomMapper.selectById(schedule.getClassroomId());
                dto.setClassroomName(classroom != null ? classroom.getName() : "未知教室");
            }
            if (schedule.getTeacherId() != null) {
                Teacher teacher = teacherMapper.selectById(schedule.getTeacherId());
                dto.setTeacherName(teacher != null ? teacher.getName() : "未知教师");
            }

            return dto;
        }).collect(Collectors.toList());

        PageResult<ScheduleResponseDTO> result = new PageResult<>();
        result.setTotal(schedulePage.getTotal());
        result.setPage(schedulePage.getCurrent());
        result.setSize(schedulePage.getSize());
        result.setRecords(dtoList);

        return Result.success("success", result);
    }

    public Result<Object> deleteSchedule(Long id) {
        CourseSchedule schedule = this.getById(id);
        if (schedule == null) {
            return Result.error(404, "排课不存在");
        }
        if (schedule.getStatus() != 0) {
            return Result.error(500, "无法删除已开始或已结束的排课！");
        }

        this.removeById(id);
        return Result.success("删除成功！", null);
    }

    public Result<List<ScheduleResponseDTO>> getAnalysisList(int streamType, String token) {
        if (token == null || !token.startsWith("mock_jwt_token_")) {
            return Result.error(401, "未授权的访问！");
        }
        String empNo = token.substring("mock_jwt_token_".length());

        Teacher currentTeacher = teacherMapper
                .selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, empNo));
        if (currentTeacher == null) {
            return Result.error(401, "无效的用户令牌！");
        }

        LambdaQueryWrapper<CourseSchedule> wrapper = new LambdaQueryWrapper<>();

        // 绝对不能返回"未开始(status=0)"的排课
        wrapper.ne(CourseSchedule::getStatus, 0);

        if (currentTeacher.getRole() == 1) {
            // 超级管理员 (role=1)
            if (streamType == 1) {
                wrapper.eq(CourseSchedule::getStatus, 1);
                wrapper.apply("NOW() BETWEEN start_time AND end_time");
            } else if (streamType == 2) {
                // 文件流看历史，不加时间强控
                wrapper.in(CourseSchedule::getStatus, 1, 2);
            }
        } else {
            // 普通老师 (role=0)
            wrapper.eq(CourseSchedule::getTeacherId, currentTeacher.getId());
            if (streamType == 1) {
                wrapper.eq(CourseSchedule::getStatus, 1); // 实时流只查进行中
                wrapper.apply("NOW() BETWEEN start_time AND end_time");
            } else if (streamType == 2) {
                wrapper.in(CourseSchedule::getStatus, 1, 2); // 文件流查进行中及已结束
            }
        }

        wrapper.orderByDesc(CourseSchedule::getCreatedAt);
        List<CourseSchedule> list = this.list(wrapper);

        List<ScheduleResponseDTO> dtoList = list.stream().map(schedule -> {
            ScheduleResponseDTO dto = new ScheduleResponseDTO();
            BeanUtils.copyProperties(schedule, dto);

            if (schedule.getCourseId() != null) {
                Course course = courseMapper.selectById(schedule.getCourseId());
                dto.setCourseName(course != null ? course.getName() : "未知课程");
            }
            if (schedule.getClassroomId() != null) {
                Classroom classroom = classroomMapper.selectById(schedule.getClassroomId());
                dto.setClassroomName(classroom != null ? classroom.getName() : "未知教室");
            }
            if (schedule.getTeacherId() != null) {
                Teacher t = teacherMapper.selectById(schedule.getTeacherId());
                dto.setTeacherName(t != null ? t.getName() : "未知教师");
            }

            return dto;
        }).collect(Collectors.toList());

        return Result.success("success", dtoList);
    }
}
