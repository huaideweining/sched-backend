package com.appliance.itwb.session.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 终端类型。
 *
 * <p><b>文件作用</b>：标识登录来源设备，"单点登录（踢同设备旧会话）"与在线终端列表分组都依赖它。</p>
 *
 * <p><b>依赖</b>：MyBatis-Plus（@EnumValue）、Jackson（@JsonValue/@JsonCreator）。</p>
 *
 * <p><b>Oracle 注意点</b>：DDL 的 CHECK 约束 CK_SYS_USER_SESSION_DEVICE 只列了这 5 个值，
 * 新增枚举值时必须同步改约束，否则插入报 ORA-02290。</p>
 */
public enum DeviceType {

    /** 电脑端。 */
    PC("PC", "电脑端"),

    /** 手机端。 */
    MOBILE("MOBILE", "手机端"),

    /** 平板。 */
    TABLET("TABLET", "平板"),

    /** 微信小程序等小程序端。 */
    MINI_PROGRAM("MINI_PROGRAM", "小程序"),

    /** 未知或自定义终端。 */
    OTHER("OTHER", "其他");

    /** 数据库存储值。 */
    @EnumValue
    private final String code;

    /** 中文描述。 */
    private final String description;

    /**
     * 构造枚举。
     *
     * @param code        数据库存储值
     * @param description 中文描述
     */
    DeviceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Jackson 序列化输出。
     *
     * @return 数据库存储值
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 按 code 解析枚举。
     *
     * @param value 数据库存储值或前端传入值
     * @return 对应枚举，未匹配到时返回 null
     */
    @JsonCreator
    public static DeviceType parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(OTHER);
    }

    /**
     * 获取中文描述。
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}
