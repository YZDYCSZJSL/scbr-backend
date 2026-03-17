package com.scbrbackend.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class RealtimeFrameResultVO {
    private Long taskId;
    private Integer status;
    private Integer frameTime;
    private Integer attendanceCount;
    private Double currentScore;
    private List<RealtimeBehaviorVO> behaviors;
}
