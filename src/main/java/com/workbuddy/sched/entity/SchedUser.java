package com.workbuddy.sched.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度用户表 SCHED_USER（R4 用户注册）。
 *
 * <p>主键 {@code USER_ID} 由业务指定（注册时取 {@code username}），故用 {@link IdType#INPUT}；
 * 用户名 {@code USERNAME} 唯一（UK_SCHED_USER_NAME），密码以 BCrypt 哈希存储（与既有手插哈希同构）。</p>
 */
@Data
@TableName("SCHED_USER")
public class SchedUser {

    /** 用户主键；注册约定 USER_ID = USERNAME，由调用方显式写入。 */
    @TableId(value = "USER_ID", type = IdType.INPUT)
    private String userId;

    /** 登录名；唯一（UK_SCHED_USER_NAME）。 */
    @TableField("USERNAME")
    private String username;

    /** BCrypt 哈希后的密码（明文不落库）。 */
    @TableField("PASSWORD")
    private String password;

    /** 状态：Y=启用 / N=停用（CK_SCHED_USER_STATUS）。 */
    @TableField("STATUS")
    private String status;

    @TableField("CREATED_AT")
    private LocalDateTime createdAt;

    @TableField("UPDATED_AT")
    private LocalDateTime updatedAt;
}
