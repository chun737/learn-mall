package com.mall.common;

import com.mall.enums.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：统一将异常转为 Result 响应
 *
 * @author 乐乐
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 1. 业务异常：使用异常自带的 code 和 message
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // 5xx = 系统级异常（如 SYSTEM_ERROR「系统繁忙」）。它意味着"代码或环境出了问题"，
        // 必须带堆栈才能定位——只记一行 message 会让本次排查彻底失去线索。
        // 4xx 类业务异常（参数错误、库存不足、资源不存在…）是正常业务分支，
        // 每个都要记堆栈会刷爆日志，只记摘要即可。
        if (e.getCode() >= 500) {
            log.error("系统级业务异常: code={}, message={}", e.getCode(), e.getMessage(), e);
        } else {
            log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        }
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 2. 参数校验异常（@Valid / @Validated 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 2.1 参数校验异常（非 @RequestBody 场景：@RequestParam / Service 层 @Validated 方法参数）
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(jakarta.validation.ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 3. 兜底异常：未知异常，返回系统错误并记录完整堆栈
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
