package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class CreateAnalysisTaskResponseDTO {
    private Long taskId;
    private Integer status; // 0-排队中, 1-分析中
}
