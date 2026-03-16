package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class RetryTaskResponseDTO {
    private Long taskId;
    private Integer status;
    private Integer retryCount;
}
