package com.workbuddy.sched.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbuddy.sched.entity.SchedTrigger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SchedTriggerMapper extends BaseMapper<SchedTrigger> {

    /**
     * 抢占到期触发器（Oracle 19c：FOR UPDATE SKIP LOCKED）。
     * 多节点同时执行也只会有一个节点拿到同一行 → 天然并发不重复。
     * shardTotal=1 时 MOD(ORA_HASH(USER_ID),1)=0 恒成立（单节点）。
     */
    @Select("SELECT * FROM SCHED_TRIGGER t " +
            "WHERE t.STATUS = #{status} " +
            "  AND t.NEXT_FIRE_TIME <= #{now} " +
            "  AND MOD(ORA_HASH(t.USER_ID), #{shardTotal}) = #{shardMod} " +
            "  AND ROWNUM <= #{limit} " +
            "FOR UPDATE SKIP LOCKED")
    @Options(flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    List<SchedTrigger> claimDue(@Param("status") String status,
                               @Param("now") LocalDateTime now,
                               @Param("shardTotal") int shardTotal,
                               @Param("shardMod") int shardMod,
                               @Param("limit") int limit);

    /** 状态 CAS：避免时间轮与扫描路径以及多节点重复抢占同一触发器 */
    @Update("UPDATE SCHED_TRIGGER SET STATUS = #{newStatus}, UPDATED_AT = SYSTIMESTAMP " +
            "WHERE TRIGGER_ID = #{id} AND STATUS = #{oldStatus}")
    int compareAndSet(@Param("id") Long id,
                      @Param("oldStatus") String oldStatus,
                      @Param("newStatus") String newStatus);
}
