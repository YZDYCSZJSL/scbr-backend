package com.scbrbackend.model.vo;

import lombok.Data;

@Data
public class ScheduleConflictItemVO {
    private Long id;
    private String courseName;
    private String teacherName;
    private String classroomName;
    private Integer weekday;
    private Integer startSectionNo;
    private Integer endSectionNo;
    private Integer startWeek;
    private Integer endWeek;
    private Integer weekType;
    private String remark;
}
