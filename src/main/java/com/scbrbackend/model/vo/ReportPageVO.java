package com.scbrbackend.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReportPageVO {
    private Long id;
    private String courseName;
    private String classroomName;
    private String teacherName;
    private Integer mediaType;
    private LocalDateTime createdAt;
    private Integer status;
    private Integer attendanceCount;
    private BigDecimal totalScore;
}
