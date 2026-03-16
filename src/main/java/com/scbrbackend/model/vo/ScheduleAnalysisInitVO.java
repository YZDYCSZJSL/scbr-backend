package com.scbrbackend.model.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalTime;

@Data
public class ScheduleAnalysisInitVO {
    private Long scheduleId;
    private String academicYear;
    private Integer semester;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private Long classroomId;
    private String classroomName;
    private Integer weekday;
    private String weekdayName;
    private Long startSectionId;
    private Integer startSectionNo;
    private Long endSectionId;
    private Integer endSectionNo;
    private String sectionRangeText;
    private String sectionTimeText;
    private Integer startWeek;
    private Integer endWeek;
    private Integer weekType;
    private String weekTypeName;
    private String weekRangeText;
    private Integer studentCount;
    private String remark;

    @JsonIgnore
    private LocalTime startSectionTime;

    @JsonIgnore
    private LocalTime endSectionTime;
}
