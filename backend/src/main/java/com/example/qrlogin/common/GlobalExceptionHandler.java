package com.example.qrlogin.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) { return Result.fail(e.getMessage()); }

    @ExceptionHandler(Exception.class)
    public Result<Void> error(Exception e) { return Result.fail("系统异常：" + e.getMessage()); }
}
