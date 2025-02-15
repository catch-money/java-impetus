package io.github.jockerCN.exception;


import io.github.jockerCN.Result;
import io.github.jockerCN.page.CustomerArgumentResolverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionController {

    private final ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();


    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionController.class);


    private String appendMessage(String message) {
        if (Objects.nonNull(attributes)) {
            message = String.format("request fail [%s]: %s", attributes.getRequest().getRequestURI(), message);
        }
        return message;
    }

    public void warn(String message, Throwable throwable) {
        logger.warn("{}", appendMessage(message), throwable);
    }

    public void error(String message, Throwable throwable) {
        logger.error(appendMessage(message), throwable);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> adviceException(BindException e) {
        final Result<Void> result = Result.failWithMsg(String.format("args bind exception: %s", e.getMessage()));
        warn(result.getMessage(), e);
        setBindResult(e, result);
        return result;
    }

    public void setBindResult(BindException e,Result<Void> result) {
        final FieldError fieldError = e.getBindingResult().getFieldError();
        if (Objects.nonNull(fieldError)) {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            result.setMessage(message);
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> adviceException(MethodArgumentNotValidException e) {
        final Result<Void> result = Result.failWithMsg(String.format("args valid exception: %s", e.getMessage()));
        warn(result.getMessage(), e);
        setBindResult(e, result);
        return result;
    }

    @ExceptionHandler(CustomerArgumentResolverException.class)
    public Result<Void> argResolverException(CustomerArgumentResolverException e) {
        final Result<Void> result = Result.failWithMsg(String.format("args resolver exception: %s", e.getMessage()));
        warn(result.getMessage(), e);
        return result;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> argResolverException(HttpMessageNotReadableException e) {
        final Result<Void> result = Result.failWithMsg(String.format("request format error: %s", e.getMessage()));
        warn(result.getMessage(), e);
        return result;
    }
}
