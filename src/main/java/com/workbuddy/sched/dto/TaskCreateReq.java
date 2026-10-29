package com.workbuddy.sched.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建任务请求（接口契约）。
 */
@Data
public class TaskCreateReq {
    @NotBlank(message = "name 必填")
    private String name;

    private String content;

    @NotBlank(message = "mode 必填(INTERVAL/DAILY/WEEKLY/MONTHLY)")
    private String mode;

    /** INTERVAL 模式必填（分钟） */
    private Integer intervalMin;

    /** DAILY/WEEKLY/MONTHLY 模式：HH24:MI */
    private String atTime;

    /** WEEKLY：1..7 */
    private Integer weekday;

    /** MONTHLY：1..31 */
    private Integer monthDay;

    private Integer priority;

    private String project;
}
