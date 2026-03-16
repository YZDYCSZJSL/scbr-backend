package com.scbrbackend.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 排课分页返回 VO（V6.1 规则型排课）
 */
@Data
public class SchedulePageVO {
    private Long id;

    private String academicYear;
    private Integer semester;

    private Long courseId;
    private String courseNo;
    private String courseName;

    private Long teacherId;
    private String teacherEmpNo;
    private String teacherName;

    private Long classroomId;
    private String classroomNo;
    private String classroomName;

    private Integer weekday;

    private Long startSectionId;
    private Integer startSectionNo;
    private String startSectionName;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startSectionTime;

    private Long endSectionId;
    private Integer endSectionNo;
    private String endSectionName;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endSectionTime;

    private Integer startWeek;
    private Integer endWeek;
    private Integer weekType;

    private Integer studentCount;
    private String remark;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
