package com.scbrbackend.model.dto;

import lombok.Data;

@Data
public class BehaviorWeightItem {
    private String behaviorType;
    private String name;
    private Double weight;
}
