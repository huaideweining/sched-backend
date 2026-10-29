package com.workbuddy.sched.controller;

import com.appliance.itwb.common.Result;
import com.workbuddy.sched.common.TenantContext;
import com.workbuddy.sched.dto.*;
import com.workbuddy.sched.service.TaskService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务接口（接口契约）。
 * 所有接口需在 Header 带 Authorization: Bearer <token>
 * POST   /api/sched/tasks        创建
 * PUT    /api/sched/tasks/{id}   更新
 * GET    /api/sched/tasks        分页查询（含下次执行）
 * POST   /api/sched/tasks/{id}/enable   启用
 * POST   /api/sched/tasks/{id}/disable  停用
 * POST   /api/sched/tasks/{id}/fire     立即执行一次（手动触发，调试用）
 * DELETE /api/sched/tasks/{id}   删除
 */
@RestController
@RequestMapping("/api/sched/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public Result<Long> create(@Valid @RequestBody TaskCreateReq req) {
        return Result.ok(taskService.create(req, TenantContext.get()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TaskUpdateReq req) {
        taskService.update(id, req, TenantContext.get());
        return Result.ok(null);
    }

    @GetMapping
    public Result<IPage<TaskResp>> list(TaskQueryReq req) {
        return Result.ok(taskService.list(req, TenantContext.get()));
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        taskService.setEnabled(id, true, TenantContext.get());
        return Result.ok(null);
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        taskService.setEnabled(id, false, TenantContext.get());
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id, TenantContext.get());
        return Result.ok(null);
    }

    @PostMapping("/{id}/fire")
    public Result<Void> fire(@PathVariable Long id) {
        taskService.fire(id, TenantContext.get());
        return Result.ok(null);
    }
}
