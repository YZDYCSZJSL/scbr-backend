package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 排课安排实体类（V6.1 规则型排课模型）
 */
@Data
@TableName("course_schedule")
public class CourseSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String academicYear;
    private Integer semester;

    private Long courseId;
    private Long teacherId;
    private Long classroomId;

    private Integer weekday;
    private Long startSectionId;
    private Long endSectionId;

    private Integer startWeek;
    private Integer endWeek;
    private Integer weekType;

    private Integer studentCount;
    private String remark;

    private Integer status; // 0-未开始, 1-进行中, 2-已结束
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
