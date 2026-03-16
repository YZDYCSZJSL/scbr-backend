package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class TaskCenterPageQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String keyword;
    private Integer status;
    private Integer mediaType;
    private String startDate;
    private String endDate;
    
    // 由后端提取Token注入，前端不可传
    private Long teacherId; 
}
