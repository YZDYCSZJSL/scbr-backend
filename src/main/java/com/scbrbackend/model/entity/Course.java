package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String courseNo;
    private String name;
    private String department;
    private Integer hours;
    private Integer status; // 1-正常, 0-停课
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
