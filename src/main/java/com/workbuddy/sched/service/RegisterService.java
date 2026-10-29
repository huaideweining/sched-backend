package com.workbuddy.sched.service;

import com.workbuddy.sched.auth.RegisterRequest;
import com.workbuddy.sched.dto.RegisterResp;

/**
 * 用户注册服务接口（R4 接口契约）。
 */
public interface RegisterService {

    /**
     * 注册新用户。
     *
     * @param req 注册请求（username 唯一；password 至少 6 位）
     * @return 注册结果（不含密码等敏感字段）
     * @throws com.workbuddy.sched.common.BizException 用户名已存在时抛出 {@code ErrorCode.USER_DUP=1004}
     */
    RegisterResp register(RegisterRequest req);
}
