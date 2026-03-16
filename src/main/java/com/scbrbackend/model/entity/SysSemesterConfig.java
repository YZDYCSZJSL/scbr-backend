package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学期配置实体类
 * 用于定义每个学期的开学日期，以便推算当前周次，支持实时流筛课
 */
@Data
@TableName("sys_semester_config")
public class SysSemesterConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String academicYear;
    private Integer semester;
    private LocalDate termStartDate;

    private Integer status; // 1-启用, 0-停用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
