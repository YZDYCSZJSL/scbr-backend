package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("analysis_task_log")
public class AnalysisTaskLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String stage;
    private Integer status; // 1-成功日志, 0-失败日志
    private String message;
    private String detailJson; // 存JSON字符串
    private LocalDateTime createdAt;
}
