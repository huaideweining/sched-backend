package com.workbuddy.sched.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbuddy.sched.common.BizException;
import com.workbuddy.sched.common.ErrorCode;
import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.dto.TodoQueryReq;
import com.workbuddy.sched.dto.TodoResp;
import com.workbuddy.sched.entity.SchedTodo;
import com.workbuddy.sched.mapper.SchedTodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 待办服务实现。待办以 Oracle 为唯一事实源，前端直连查询（方案 A）。
 */
@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

    private final SchedTodoMapper todoMapper;

    @Override
    public IPage<TodoResp> list(TodoQueryReq req, String userId) {
        Page<SchedTodo> page = new Page<>(req.getPage(), req.getSize());
        LambdaQueryWrapper<SchedTodo> w = new LambdaQueryWrapper<>();
        if (req.getDone() != null) w.eq(SchedTodo::getDone, req.getDone());
        if (req.getProject() != null) w.eq(SchedTodo::getProject, req.getProject());
        w.orderByDesc(SchedTodo::getCreatedDate);
        IPage<SchedTodo> p = todoMapper.selectPage(page, w);
        Page<TodoResp> resp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        resp.setRecords(p.getRecords().stream().map(t -> {
            TodoResp r = new TodoResp();
            r.setTodoId(t.getTodoId());
            r.setTitle(t.getTitle());
            r.setPriority(t.getPriority());
            r.setDueDate(t.getDueDate());
            r.setDone(t.getDone());
            r.setProject(t.getProject());
            r.setSourceTaskId(t.getSourceTaskId());
            r.setScheduledFireTime(t.getScheduledFireTime());
            r.setCreatedDate(t.getCreatedDate());
            return r;
        }).toList());
        return resp;
    }

    @Override
    @Transactional
    public void setDone(Long todoId, boolean done, String userId) {
        SchedTodo t = todoMapper.selectById(todoId);
        if (t == null || !userId.equals(t.getUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "todo not found");
        }
        t.setDone(done ? "Y" : "N");
        todoMapper.updateById(t);
    }
}
