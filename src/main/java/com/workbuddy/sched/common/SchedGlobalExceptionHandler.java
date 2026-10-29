package com.workbuddy.sched.common;

import com.appliance.itwb.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（R2 修复项，限定 com.workbuddy.sched 包，避免与在役 wb 处理器冲突）。
 * 将业务异常/参数校验异常统一收敛为 Result{code,message}，避免 BizException 冒泡成 HTTP 500。
 */
@RestControllerAdvice(basePackages = "com.workbuddy.sched")
public class SchedGlobalExceptionHandler {

    /** 业务异常：按约定返回 HTTP 200 + Result.fail(code,msg)，由前端据 code 判断 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return ResponseEntity.ok(Result.fail(e.getCode(), e.getMessage()));
    }

    /** 参数校验失败（@Valid DTO）：收敛为 PARAM_INVALID */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> ((FieldError) fe).getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, msg));
    }

    /** 兜底：未预期异常返回 INTERNAL_ERROR（HTTP 500） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR, e.getMessage()));
    }
}
