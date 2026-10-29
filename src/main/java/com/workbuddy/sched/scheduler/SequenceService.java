package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.mapper.IdSeqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一主键生成（走 Oracle 序列，对应 DDL）。
 */
@Service
@RequiredArgsConstructor
public class SequenceService {
    private final IdSeqMapper seq;

    public Long nextTask() { return seq.nextTask(); }
    public Long nextTrigger() { return seq.nextTrigger(); }
    public Long nextFire() { return seq.nextFire(); }
    public Long nextTodo() { return seq.nextTodo(); }
}
