package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_detail")
public class AnalysisDetail {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    
    private Integer frameTime;
    
    private String behaviorType;
    
    private Integer count;
    
    private String boundingBoxes;
    
    // 0-全量明细(文件流), 1-趋势聚合(实时流走势), 2-违规抓拍(实时流铁证)
    private Integer recordType;
    
    // 关键帧抓拍图片OSS链接 (仅 record_type=2 时有值)
    private String snapshotUrl;
    
    private LocalDateTime createdAt;
}
