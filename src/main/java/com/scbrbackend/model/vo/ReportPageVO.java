package com.scbrbackend.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReportPageVO {
    private Long id;
    private Long taskId;
    private Long scheduleId;
    private String courseName;
    private String classroomName;
    private String teacherName;
    private String classTimeText;
    private Integer mediaType;
    private LocalDateTime createdAt;
    private Integer status;
    private Integer studentCount;
    private Integer attendanceCount;
    private BigDecimal attendanceRate;
    private BigDecimal totalScore;
    private String reportLevel;
    private Integer abnormalFlag;
}
