package com.workbuddy.sched.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbuddy.sched.entity.SchedNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SchedNodeMapper extends BaseMapper<SchedNode> {
}
