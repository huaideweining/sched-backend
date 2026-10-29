package com.workbuddy.sched.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workbuddy.sched.dto.TodoQueryReq;
import com.workbuddy.sched.dto.TodoResp;

/**
 * 待办服务接口（接口契约）。
 */
public interface TodoService {
    IPage<TodoResp> list(TodoQueryReq req, String userId);
    void setDone(Long todoId, boolean done, String userId);
}
