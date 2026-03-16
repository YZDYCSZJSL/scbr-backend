package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 系统节次时间配置实体类
 */
@Data
@TableName("sys_section_time")
public class SysSectionTime {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer sectionNo;
    private String sectionName;
    private LocalTime startTime;
    private LocalTime endTime;

    private Integer status;
    private Integer sortNo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
