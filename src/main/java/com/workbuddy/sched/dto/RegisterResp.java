package com.workbuddy.sched.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册响应（R4 接口契约）。
 * 不回传 password 等敏感字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResp {
    private String userId;
    private String username;
    /** 状态：Y=启用 / N=停用 */
    private String status;
}
