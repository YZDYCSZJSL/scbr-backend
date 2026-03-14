package com.scbrbackend.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportDetailVO {
    private Long id;
    private String courseName;
    private String teacherName;
    private String classroomName;
    private Integer mediaType;
    private LocalDateTime createdAt;
    private Integer status;
    private BigDecimal totalScore;
    private Integer attendanceCount;
    private String fileUrl;
    
    private List<AnalysisDetailVO> detailList;
}
