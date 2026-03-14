package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分析任务实体类
 */
@Data
@TableName("analysis_task")
public class AnalysisTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private Long classroomId;
    private Long scheduleId;
    private Long fileId;
    private Integer mediaType; // 1-图片, 2-视频, 3-实时流
    private Integer status; // 0-排队中, 1-分析中, 2-成功, 3-失败
    private Integer attendanceCount;
    private BigDecimal totalScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
