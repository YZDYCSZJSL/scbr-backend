package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class ModelFailCallbackDTO {
    private Long taskId;
    private String errorCode;
    private String errorMessage;
}
