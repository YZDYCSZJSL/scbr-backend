package com.scbrbackend.model.vo;

import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class LoginLogPageVO {
    private Long id;
    private String empNo;
    private String userName;
    private Integer loginStatus;
    private String loginMessage;
    private String ipAddress;
    private String userAgent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
