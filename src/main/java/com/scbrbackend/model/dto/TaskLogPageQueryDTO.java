package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class TaskLogPageQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private Long taskId;
    private String stage;
    private Integer status;
    private String startDate;
    private String endDate;
}
