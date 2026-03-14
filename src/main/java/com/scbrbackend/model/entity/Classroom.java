package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("classroom")
public class Classroom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomNo;
    private String name;
    private String location;
    private Integer capacity;
    private Integer status; // 1-可用, 0-维护中
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
