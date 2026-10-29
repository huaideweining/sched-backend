package com.workbuddy.sched.dto;

import lombok.Data;

/**
 * 任务查询请求（接口契约）。
 */
@Data
public class TaskQueryReq {
    private String nameLike;
    private String mode;
    private String enabled;       // Y/N
    private Integer page = 1;
    private Integer size = 20;
}
