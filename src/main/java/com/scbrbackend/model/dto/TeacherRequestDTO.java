package com.scbrbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TeacherRequestDTO {
    private Long id;
    @JsonProperty("emp_no")
    private String empNo;
    private String password;
    private String name;
    private String phone;
    private String department;
    private Integer role;
    private Integer status;
}
