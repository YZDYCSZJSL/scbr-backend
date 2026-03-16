package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class LoginLogPageQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String empNo;
    private String userName;
    private Integer loginStatus;
    private String startDate;
    private String endDate;
}
