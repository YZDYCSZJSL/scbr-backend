package com.scbrbackend.model.vo;

import lombok.Data;


@Data
public class RealtimeAnalysisStartVO {
    private Long taskId;
    private Long scheduleId;
    private Integer mediaType;
    private Integer status;
    private String startTime;
}
