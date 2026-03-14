package com.scbrbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleResponseDTO {
    private Long id;
    private Long courseId;
    private String courseName;
    private Long classroomId;
    private String classroomName;
    private Long teacherId;
    private String teacherName;
    private Integer studentCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer status;
}
