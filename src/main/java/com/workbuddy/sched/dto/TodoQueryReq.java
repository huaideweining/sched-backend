package com.workbuddy.sched.dto;

import lombok.Data;

/**
 * 待办查询请求（接口契约）。
 */
@Data
public class TodoQueryReq {
    private String done;          // Y/N
    private String project;
    private Integer page = 1;
    private Integer size = 20;
}
