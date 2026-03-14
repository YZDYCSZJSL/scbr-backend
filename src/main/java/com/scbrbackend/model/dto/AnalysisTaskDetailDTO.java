package com.scbrbackend.model.dto;

import lombok.Data;
import java.util.List;


@Data
public class AnalysisTaskDetailDTO {
    private Long taskId;
    private Integer status;
    private Integer mediaType;
    private Integer attendanceCount;
    private Double totalScore;
    private List<DetailItem> details;

    @Data
    public static class DetailItem {
        private Integer frameTime;
        private String behaviorType;
        private Integer count;
        private List<List<Double>> boundingBoxes;
    }
}
