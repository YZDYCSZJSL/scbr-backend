package com.scbrbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskLogItemDTO {
    private Long id;
    private String stage;
    private Integer status;
    private String message;
    private Object detailJson; // Map或JSON对象
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
