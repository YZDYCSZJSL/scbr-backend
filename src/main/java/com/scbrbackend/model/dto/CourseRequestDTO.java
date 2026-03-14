package com.scbrbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CourseRequestDTO {
    private Long id;
    @JsonProperty("course_no")
    private String courseNo;
    private String name;
    private String department;
    private Integer hours;
    private Integer status;
}
