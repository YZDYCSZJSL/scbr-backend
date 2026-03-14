package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class AnalysisTaskStatusDTO {
    private Long taskId;
    private Integer status;          // 0-排队中, 1-分析中, 2-成功, 3-失败
    private Integer mediaType;       // 1-图片, 2-视频, 3-实时流
    private Integer attendanceCount;
    private Double totalScore;
    private Integer progress;        // 0~100，可选
    private String errorMessage;
}
