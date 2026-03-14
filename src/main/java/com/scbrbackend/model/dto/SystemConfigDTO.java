package com.scbrbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemConfigDTO {
    private Long id;
    @JsonProperty("config_name")
    private String configName;
    @JsonProperty("is_active")
    private Integer isActive;
    @JsonProperty("config_content")
    private String configContent;
    private String description;
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
