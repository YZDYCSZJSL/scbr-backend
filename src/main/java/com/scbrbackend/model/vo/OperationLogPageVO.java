package com.scbrbackend.model.vo;

import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class OperationLogPageVO {
    private Long id;
    private String empNo;
    private String userName;
    private String moduleName;
    private String operationType;
    private Long businessId;
    private String operationDesc;
    private Integer operationStatus;
    private String requestMethod;
    private String requestUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
