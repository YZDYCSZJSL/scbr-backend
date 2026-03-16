package com.scbrbackend.model.vo;

import lombok.Data;
import java.time.LocalTime;

@Data
public class SectionTimeVO {
    private Long id;
    private Integer sectionNo;
    private String sectionName;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer status;
    private Integer sortNo;
}
