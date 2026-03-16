package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class ScheduleConflictCheckDTO {
    private Long id;
    private String academicYear;
    private Integer semester;
    private Long teacherId;
    private Long classroomId;
    private Integer weekday;
    private Long startSectionId;
    private Long endSectionId;
    private Integer startWeek;
    private Integer endWeek;
    private Integer weekType;
}
