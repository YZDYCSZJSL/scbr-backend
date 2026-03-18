package com.scbrbackend.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ModelFileCallbackDTO {
    private Long taskId;
    private Integer mediaType;       // 1-图片, 2-视频
    private Integer attendanceCount;
    private List<Detail> details;
    private ModelMeta modelMeta;

    @Data
    public static class Detail {
        private Integer frameTime;
        private String behaviorType;
        private Integer count;
        private Integer recordType;
        private String snapshotUrl;
        private List<List<Double>> boundingBoxes;
    }

    @Data
    public static class ModelMeta {
        private String modelVersion;
        private Integer sampleIntervalSec;
        private Integer inferenceMs;
    }
}
