package com.workbuddy.sched.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbuddy.sched.entity.SchedTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SchedTaskMapper extends BaseMapper<SchedTask> {
}
