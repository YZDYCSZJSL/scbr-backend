package com.scbrbackend.model.vo;

import lombok.Data;
import java.time.LocalTime;

@Data
public class WeekScheduleItemVO {
    private Long id;
    private Integer weekday;
    
    private Long startSectionId;
    private Integer startSectionNo;
    private String startSectionName;
    private LocalTime startSectionTime;
    
    private Long endSectionId;
    private Integer endSectionNo;
    private String endSectionName;
    private LocalTime endSectionTime;
    
    private String courseName;
    private String teacherName;
    private String classroomName;
    
    private Integer startWeek;
    private Integer endWeek;
    private Integer weekType;
    private Integer studentCount;
    private String remark;
}
