package com.appliance.itwb.common;

import java.io.Serializable;

/**
 * 统一接口返回结构。
 *
 * <p><b>文件作用</b>：全站 REST 接口的统一包装体，避免每个方法各写各的格式，
 * 前端只认一套契约。</p>
 *
 * <p><b>依赖</b>：无第三方依赖，JDK 自带即可。</p>
 *
 * <p><b>Oracle 注意点</b>：无。本类与数据库无关。</p>
 *
 * <p><b>字段约定</b>：code = 0 表示成功（非 HTTP 状态码，HTTP 状态由 @ResponseStatus 控制）；
 * success 是给前端"一眼判断"用的冗余位，避免前端去解析 code。</p>
 *
 * <p><b>使用方式</b>：{@code return Result.ok(data);}</p>
 */
public class Result<T> implements Serializable {

    /** 成功码。 */
    public static final int CODE_SUCCESS = 0;

    /** 失败码。 */
    public static final int CODE_FAIL = 500;

    private static final long serialVersionUID = 1L;

    /** 业务码，0 为成功。 */
    private int code;

    /** 成功标志。 */
    private boolean success;

    /** 提示信息，可前端直接展示。 */
    private String msg;

    /** 业务数据，失败时为 null。 */
    private T data;

    /** 服务端时间戳（毫秒），排障时用于与客户端对时。 */
    private long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构造返回体。
     *
     * @param code 业务码
     * @param msg  提示信息
     * @param data 业务数据，可为 null
     */
    public Result(int code, String msg, T data) {
        this();
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.success = (code == CODE_SUCCESS);
    }

    /**
     * 成功返回（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功返回体
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_SUCCESS, "success", data);
    }

    /**
     * 成功返回（无数据）。
     *
     * @param <T> 数据类型
     * @return 成功返回体
     */
    public static <T> Result<T> ok() {
        return new Result<>(CODE_SUCCESS, "success", null);
    }

    /**
     * 失败返回。
     *
     * @param msg 提示信息
     * @param <T> 数据类型
     * @return 失败返回体
     */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(CODE_FAIL, msg, null);
    }

    /**
     * 失败返回（自定义码）。
     *
     * @param code 业务码
     * @param msg  提示信息
     * @param <T>  数据类型
     * @return 失败返回体
     */
    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
