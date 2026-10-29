package com.workbuddy.sched.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workbuddy.sched.dto.TaskCreateReq;
import com.workbuddy.sched.dto.TaskQueryReq;
import com.workbuddy.sched.dto.TaskResp;
import com.workbuddy.sched.dto.TaskUpdateReq;

/**
 * 任务服务接口（接口契约）。
 */
public interface TaskService {

    /** 创建任务（同时生成触发器） */
    Long create(TaskCreateReq req, String userId);

    /** 更新任务（必要时重算触发器） */
    void update(Long taskId, TaskUpdateReq req, String userId);

    /** 分页查询当前用户任务（含下次执行时间） */
    IPage<TaskResp> list(TaskQueryReq req, String userId);

    /** 启用 / 停用 */
    void setEnabled(Long taskId, boolean enabled, String userId);

    /** 删除任务（级联删除触发器） */
    void delete(Long taskId, String userId);

    /** 立即执行一次（手动触发，用于调试运行） */
    void fire(Long taskId, String userId);
}
