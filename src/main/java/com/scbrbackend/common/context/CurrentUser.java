package com.scbrbackend.common.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentUser {
    private Long id;
    private String empNo;
    private String name;
    private Integer role;
    private String department;
}
