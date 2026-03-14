package com.scbrbackend.model.dto.dashboard;

import lombok.Data;

@Data
public class DashboardUserInfo {
    private String empNo;
    private String name;
    private Integer role;
    private String department;
}
