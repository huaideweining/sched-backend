package com.workbuddy.sched.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 序列取值（Oracle 序列，对应 DDL 中 SEQ_SCHED_*）。
 */
@Mapper
public interface IdSeqMapper {
    @Select("SELECT SEQ_SCHED_TASK.NEXTVAL FROM DUAL")
    Long nextTask();

    @Select("SELECT SEQ_SCHED_TRIGGER.NEXTVAL FROM DUAL")
    Long nextTrigger();

    @Select("SELECT SEQ_SCHED_FIRE_LOG.NEXTVAL FROM DUAL")
    Long nextFire();

    @Select("SELECT SEQ_SCHED_TODO.NEXTVAL FROM DUAL")
    Long nextTodo();
}
