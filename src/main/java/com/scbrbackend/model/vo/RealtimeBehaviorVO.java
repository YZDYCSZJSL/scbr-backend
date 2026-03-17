package com.scbrbackend.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class RealtimeBehaviorVO {
    private String behaviorType;
    private Integer count;
    private List<List<Double>> boundingBoxes;
    private String snapshotUrl;
}
