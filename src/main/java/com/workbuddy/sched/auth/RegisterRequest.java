package com.workbuddy.sched.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求（R4 接口契约）。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "username 必填")
    @Size(min = 3, max = 32, message = "username 长度需 3~32")
    private String username;

    @NotBlank(message = "password 必填")
    @Size(min = 6, max = 128, message = "password 长度需 6~128")
    private String password;
}
