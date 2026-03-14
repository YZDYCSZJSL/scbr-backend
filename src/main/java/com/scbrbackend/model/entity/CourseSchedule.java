package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程安排实体类
 */
@Data
@TableName("course_schedule")
public class CourseSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long classroomId;
    private Long teacherId;
    private Integer studentCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status; // 0-未开始, 1-进行中, 2-已结束
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
