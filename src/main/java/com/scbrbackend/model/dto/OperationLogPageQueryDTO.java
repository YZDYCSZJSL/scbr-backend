package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class OperationLogPageQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String empNo;
    private String moduleName;
    private String operationType;
    private Integer operationStatus;
    private String startDate;
    private String endDate;
}
