package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师实体类
 */
@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String empNo;
    private String password;
    private String name;
    private String phone;
    private String department;
    private Integer role; // 0-普通老师, 1-超级管理员
    private Integer status; // 1-正常, 0-禁用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
