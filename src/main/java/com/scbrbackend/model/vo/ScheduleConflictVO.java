package com.scbrbackend.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class ScheduleConflictVO {
    private Boolean hasConflict;
    private Boolean teacherConflict;
    private Boolean classroomConflict;
    private List<ScheduleConflictItemVO> teacherConflictList;
    private List<ScheduleConflictItemVO> classroomConflictList;
}
