package com.scbrbackend.model.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReportPageQueryDTO {
    /**
     * 当前页码，默认 1
     */
    private Integer page = 1;

    /**
     * 每页记录数，默认 10
     */
    private Integer size = 10;

    /**
     * 搜索关键词(匹配课程名称或教师)
     */
    private String keyword;

    /**
     * 搜索起始日期 (YYYY-MM-DD)
     */
    private LocalDate startDate;

    /**
     * 搜索结束日期 (YYYY-MM-DD)
     */
    private LocalDate endDate;
}
