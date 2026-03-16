package com.scbrbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TaskCenterDetailDTO {
    private Long id;
    private String courseName;
    private String teacherName;
    private String classroomName;
    private Long scheduleId;
    private Integer mediaType;
    private Integer status;
    private Long fileId;
    private String fileName;
    private String fileUrl;
    private Integer attendanceCount;
    private BigDecimal totalScore;
    private Integer retryCount;
    private String failReason;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;
    
    private Long durationSeconds;
}
