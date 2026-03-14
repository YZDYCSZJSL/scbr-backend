package com.scbrbackend.model.vo;

import lombok.Data;

@Data
public class AnalysisDetailVO {
    private Integer recordType;
    private Integer frameTime;
    private String behaviorType;
    private Integer count;
    private Object boundingBoxes;
    private String snapshotUrl;
}
