package com.scbrbackend.model.vo;

import lombok.Data;


@Data
public class RealtimeTaskStopVO {
    private Long taskId;
    private Integer status;
    private Integer attendanceCount;
    private Double totalScore;
    private String startTime;
    private String finishTime;
}
