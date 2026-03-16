package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
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
    private LocalDateTime createdAt;
}
