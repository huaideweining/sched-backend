package com.workbuddy.sched.controller;

import com.appliance.itwb.common.Result;
import com.workbuddy.sched.common.TenantContext;
import com.workbuddy.sched.dto.TodoQueryReq;
import com.workbuddy.sched.dto.TodoResp;
import com.workbuddy.sched.service.TodoService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 待办接口（接口契约）。前端直连后端查询（方案 A）。
 * GET    /api/sched/todos           分页查询（user-scoped）
 * POST   /api/sched/todos/{id}/done 标记完成
 */
@RestController
@RequestMapping("/api/sched/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public Result<IPage<TodoResp>> list(TodoQueryReq req) {
        return Result.ok(todoService.list(req, TenantContext.get()));
    }

    @PostMapping("/{id}/done")
    public Result<Void> done(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean done) {
        todoService.setDone(id, done, TenantContext.get());
        return Result.ok(null);
    }
}
