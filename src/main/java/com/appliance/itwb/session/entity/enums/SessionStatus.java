package com.appliance.itwb.session.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 会话逻辑状态。
 *
 * <p><b>文件作用</b>：与 SYS_USER_SESSION.STATUS 列一一对应，同时用于 Java 侧状态判断，
 * 避免到处写魔法字符串。</p>
 *
 * <p><b>依赖</b>：MyBatis-Plus（@EnumValue）、Jackson（@JsonValue/@JsonCreator）。</p>
 *
 * <p><b>Oracle 注意点</b>：枚举 code 与 DDL 的 CHECK 约束取值必须完全一致，
 * 改这里就要同步改 CK_SYS_USER_SESSION_STATUS，否则插入时触发 ORA-02290。</p>
 *
 * <p><b>重要区分</b>：STATUS 是"逻辑状态"（人为/业务写入），EXPIRE_TIME 是"时间边界"（时间到了自动失效）。
 * 一条会话可能 STATUS=ACTIVE 但 EXPIRE_TIME 已过（还没被清理任务回收），
 * 也可能 STATUS=EXPIRED 但时间未到。校验时必须两个条件同时判断。</p>
 */
public enum SessionStatus {

    /** 有效，可被校验通过。 */
    ACTIVE("ACTIVE", "在线有效"),

    /** 超时被清理任务或校验动作回收为失效。 */
    EXPIRED("EXPIRED", "已超时失效"),

    /** 用户主动登出。 */
    LOGOUT("LOGOUT", "已登出"),

    /** 被踢下线（同设备顶替、超上限淘汰、管理端踢人）。 */
    KICKED("KICKED", "已被踢下线");

    /** 数据库存储值。 */
    @EnumValue
    private final String code;

    /** 中文描述，仅用于接口展示。 */
    private final String description;

    /**
     * 构造枚举。
     *
     * @param code        数据库存储值
     * @param description 中文描述
     */
    SessionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Jackson 序列化时输出 code，而不是枚举名，保证前端拿到的是 DB 约定值。
     *
     * @return 数据库存储值
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * Jackson 反序列化入口。
     *
     * @param value 数据库存储值
     * @return 对应枚举，未知值返回 null 由调用方拒绝
     */
    @JsonCreator
    public static SessionStatus parse(String value) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取中文描述。
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否是终态（不可再被校验通过）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
