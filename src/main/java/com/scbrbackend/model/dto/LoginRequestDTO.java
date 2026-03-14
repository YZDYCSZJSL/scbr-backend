package com.scbrbackend.model.dto;

import lombok.Data;

/**
 * 登录请求 DTO
 */
@Data
public class LoginRequestDTO {
    private String username;
    private String password;
}
