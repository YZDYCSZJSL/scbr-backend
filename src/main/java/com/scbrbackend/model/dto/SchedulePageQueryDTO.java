package com.scbrbackend.model.dto;

import lombok.Data;

/**
 * 排课分页查询 DTO
 */
@Data
public class SchedulePageQueryDTO {
    private Integer page = 1;
    private Integer size = 10;

    private String academicYear;
    private Integer semester;
    private Long courseId;
    private Long teacherId;
    private Long classroomId;
    private Integer weekday;

    /** 课程/教师/教室关键词模糊查询 */
    private String keyword;

    /** 计算 SQL OFFSET */
    public int getOffset() {
        return (page != null && size != null) ? (page - 1) * size : 0;
    }
}
