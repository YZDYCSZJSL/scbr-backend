package com.scbrbackend.model.vo;

import lombok.Data;


@Data
public class RealtimeTaskStatusVO {
    private Long taskId;
    private Integer mediaType;
    private Integer status;
    private Integer attendanceCount;
    private Double totalScore;
    private String startTime;
    private String finishTime;
    private String failReason;
}
