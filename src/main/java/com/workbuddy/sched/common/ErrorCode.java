package com.workbuddy.sched.common;

/**
 * 业务错误码（接口契约的一部分）。
 */
public final class ErrorCode {
    public static final int OK = 0;
    public static final int PARAM_INVALID = 1001;
    public static final int UNAUTHORIZED = 1002;
    public static final int FORBIDDEN = 1003;
    public static final int USER_DUP = 1004;
    public static final int TASK_NOT_FOUND = 2001;
    public static final int TASK_NAME_DUP = 2002;
    public static final int TRIGGER_CLAIM_FAIL = 3001;
    public static final int FIRE_IDEMPOTENT = 3002;
    public static final int INTERNAL_ERROR = 5000;

    private ErrorCode() {}
}
