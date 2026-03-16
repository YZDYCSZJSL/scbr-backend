package com.scbrbackend.model.vo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SemesterConfigOptionVO {
    private Long id;
    private String academicYear;
    private Integer semester;
    private String label;
    private Integer status;
    private LocalDate termStartDate;
}
