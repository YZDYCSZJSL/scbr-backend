package com.scbrbackend.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ConfigImportDTO {
    private String config_name;
    private String description;
    private Integer is_active;
    private List<BehaviorWeightItem> config_content;
}
