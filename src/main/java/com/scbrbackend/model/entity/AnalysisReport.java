package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课堂评估报告快照实体
 */
@Data
@TableName("analysis_report")
public class AnalysisReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long scheduleId;
    
    private BigDecimal attendanceRate;
    private BigDecimal attendanceScore;
    private BigDecimal focusScore;
    private BigDecimal interactionScore;
    private BigDecimal disciplineScore;
    private BigDecimal totalScore;
    
    private String reportLevel;
    private Integer abnormalFlag;
    private String summaryText;
    private String suggestionText;
    
    // 存储JSON字符串
    private String behaviorStatsJson;
    private String trendDataJson;
    private String abnormalMomentsJson;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
