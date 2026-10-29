package com.appliance.itwb.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;
import com.workbuddy.sched.common.BizException;
import com.workbuddy.sched.common.ErrorCode;

/**
 * 全局异常处理器。
 *
 * <p><b>文件作用</b>：把任何未捕获异常兜底成 {@link Result} 结构，
 * 防止抛栈信息给前端、也防止前端拿到非 JSON 响应导致解析失败。</p>
 *
 * <p><b>依赖</b>：spring-web（@RestControllerAdvice）。</p>
 *
 * <p><b>Oracle 注意点</b>：Oracle 唯一约束冲突（ORA-00001）与 ORA-12899 字段超长在这里被
 * 翻译成业务提示，避免把数据库错误码暴露给终端用户。</p>
 *
 * <p><b>注意</b>：生产环境不建议把异常堆栈打进 msg，这里只记录到服务端日志。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 认证失败处理：与系统异常区分，返回 401 与明确提示。
     *
     * @param e 认证异常
     * @return 统一返回体
     */
    @ExceptionHandler(AuthException.class)
    public Result<String> handleAuthException(AuthException e) {
        // 认证失败属正常业务分支，不打 error 堆栈
        log.warn("[session-server] 认证失败: {}", e.getMessage());
        return Result.fail(401, e.getMessage());
    }

    /**
     * 业务异常（BizException，如 USER_DUP=1004）：按异常自带 code 返回，不被兜底成 500。
     *
     * <p>注意：{@code register} 接口已合并进本包（com.appliance.itwb）的 AuthController，
     * 因此 workbuddy 的 {@code SchedGlobalExceptionHandler}（basePackages=com.workbuddy.sched）
     * 不再对其生效；若此处不专门处理 BizException，重复注册会被 {@link #handleException(Exception)}
     * 兜底成 500，误导前端。故在此统一接管业务异常码。</p>
     */
    @ExceptionHandler(BizException.class)
    public Result<String> handleBiz(BizException e) {
        log.warn("[session-server] 业务异常 code={}: {}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败（@Valid 不通过）：返回 PARAM_INVALID(1001) 与字段级错误，避免被兜底成 500。
     *
     * <p>调度侧控制器（com.workbuddy.sched）没有独立的异常处理器，其校验异常会落到本全局
     * 处理器；若不专门处理，会被 {@link #handleException(Exception)} 兜底成 500，
     * 前端既拿不到字段错误也误以为服务异常。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[session-server] 参数校验失败: {}", detail);
        return Result.fail(ErrorCode.PARAM_INVALID, "参数校验失败：" + detail);
    }

    /**
     * 请求体不可读（JSON 解析失败 / 非 UTF-8 编码）：返回 400 明确提示，避免被兜底成 500。
     *
     * <p>典型场景：客户端以非 UTF-8（如 GBK）发送含中文的请求体，Jackson 解析撞上非法字节。
     * 此前会被兜底成 500「服务异常」，极具误导性；这里直接点明编码问题。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("[session-server] 请求体不可读: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, "请求体格式错误，请使用 UTF-8 编码的 JSON");
    }

    /**
     * 兜底异常处理。
     *
     * @param e 异常
     * @return 统一返回体
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        log.error("[session-server] 未捕获异常", e);

        // 唯一约束冲突：多为并发重复提交，提示用户重试
        if (message.contains("ORA-00001")) {
            return Result.fail(1001, "数据冲突，请重试");
        }
        // 插入值超出列宽：多为设备信息过长，提示精简后重试
        if (message.contains("ORA-12899") || message.contains("ORA-01461")) {
            return Result.fail(1002, "请求内容过长，请精简后重试");
        }
        return Result.fail(500, "服务异常，请稍后重试");
    }
}
