package com.hewei.hzyjy.xunzhi.common.web;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.hewei.hzyjy.xunzhi.common.convention.errorcode.BaseErrorCode;
import com.hewei.hzyjy.xunzhi.common.convention.exception.AbstractException;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
import java.util.Optional;

/**
 * REST 接口的统一异常处理器。
 *
 * <p>负责将参数、校验、业务和未知系统异常转换为统一响应；日志只记录请求元数据与异常摘要，
 * 不记录请求正文，避免简历、岗位描述等业务内容被写入日志。</p>
 */
@Component("globalExceptionHandlerByAdmin")
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = {MethodArgumentNotValidException.class, BindException.class})
    public Result validExceptionHandler(HttpServletRequest request, Exception ex) {
        BindingResult bindingResult = ex instanceof MethodArgumentNotValidException methodArgumentNotValidException
                ? methodArgumentNotValidException.getBindingResult()
                : ((BindException) ex).getBindingResult();
        String exceptionStr = extractBindingMessage(bindingResult);
        log.error("[请求方法={}] [请求地址={}] [业务异常={}]", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    public Result constraintViolationExceptionHandler(HttpServletRequest request, ConstraintViolationException ex) {
        String exceptionStr = ex.getConstraintViolations().stream()
                .findFirst()
                .map(item -> item.getMessage())
                .orElse(StrUtil.EMPTY);
        log.error("[请求方法={}] [请求地址={}] [校验异常={}]", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public Result httpMessageNotReadableExceptionHandler(HttpServletRequest request, HttpMessageNotReadableException ex) {
        String exceptionStr = "请求体不能为空或格式错误";
        Throwable rootCause = ex.getMostSpecificCause();
        String rootMessage = rootCause == null ? ex.getMessage() : rootCause.getMessage();
        // 不记录请求正文，避免在日志中泄露 JD 或简历内容。
        log.warn("请求体解析失败 method={} uri={} contentType={} contentLength={} 原因={}",
                request.getMethod(), getUrl(request), request.getContentType(), request.getContentLengthLong(), rootMessage);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    public Result illegalArgumentExceptionHandler(HttpServletRequest request, IllegalArgumentException ex) {
        log.error("[请求方法={}] [请求地址={}] [参数异常={}]", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), ex.getMessage());
    }

    @ExceptionHandler(value = {AbstractException.class})
    public Result abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[请求方法={}] [请求地址={}] [业务异常={}]", request.getMethod(), request.getRequestURL().toString(), ex.toString(), ex.getCause());
            return Results.failure(ex);
        }
        log.error("[请求方法={}] [请求地址={}] [业务异常={}]", request.getMethod(), request.getRequestURL().toString(), ex.toString());
        return Results.failure(ex);
    }

    @ExceptionHandler(value = Throwable.class)
    public Result defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[请求方法={}] [请求地址={}] [系统异常]", request.getMethod(), getUrl(request), throwable);
        if (Objects.equals(throwable.getClass().getSuperclass().getSimpleName(), AbstractException.class.getSimpleName())) {
            String errorCode = ReflectUtil.getFieldValue(throwable, "errorCode").toString();
            String errorMessage = ReflectUtil.getFieldValue(throwable, "errorMessage").toString();
            return Results.failure(errorCode, errorMessage);
        }
        return Results.failure();
    }

    private String getUrl(HttpServletRequest request) {
        if (StringUtils.isEmpty(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }

    private String extractBindingMessage(BindingResult bindingResult) {
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        return Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
    }
}
