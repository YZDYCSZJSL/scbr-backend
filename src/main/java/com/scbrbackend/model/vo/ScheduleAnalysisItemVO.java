package com.scbrbackend.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalTime;

/**
 * analysis-list 返回 VO（V6.1 规则型排课）
 * 用于"按排课发起分析"场景中选择课程
 */
@Data
public class ScheduleAnalysisItemVO {
    private Long id;

    private String courseName;
    private String teacherName;
    private String classroomName;

    private String academicYear;
    private Integer semester;
    private Integer weekday;

    private Integer startSectionNo;
    private Integer endSectionNo;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startSectionTime;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endSectionTime;

    private Integer startWeek;
    private Integer endWeek;
    private Integer weekType;

    private Integer studentCount;
}
