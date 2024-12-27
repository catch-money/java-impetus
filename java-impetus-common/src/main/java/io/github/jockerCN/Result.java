package io.github.jockerCN;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import static io.github.jockerCN.Result.StatusCode.*;


/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@AllArgsConstructor
public class Result<T> {

    private T data;

    private int code;

    private String message;


    public boolean isOk() {
        return code == SUCCESS.code;
    }

    public boolean isError() {
        return code != SUCCESS.code;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(data, SUCCESS.code, SUCCESS.message);
    }

    public static <T> Result<T> warn(T data) {
        return new Result<>(data, WARN.code, WARN.message);
    }

    public static <T> Result<T> warn(T data,String message) {
        return new Result<>(data, WARN.code, message);
    }

    public static <T> Result<T> warn() {
        return new Result<>(null, WARN.code, WARN.message);
    }

    public static <T> Result<T> warn(String message) {
        return new Result<>(null, WARN.code, message);
    }

    public static <T> Result<T> ok() {
        return new Result<>(null, SUCCESS.code, SUCCESS.message);
    }

    public static <T> Result<T> okWithCodeAndMsg(T data, int code, String message) {
        return new Result<>(data, code, message);
    }

    public static <T> Result<T> fail() {
        return new Result<>(null, FAIL.code, FAIL.message);
    }

    public static <T> Result<T> failEmpty() {
        return new Result<>(null, FAIL.code, "");
    }

    public static <T> Result<T> failWithMsg(String message) {
        return new Result<>(null, FAIL.code, message);
    }

    public static <T> Result<T> failWithUNAuth() {
        return new Result<>(null, UN_AUTHORIZE.code, UN_AUTHORIZE.message);
    }


    public static <T> Result<T> failWithUNAuth(String message) {
        return new Result<>(null, UN_AUTHORIZE.code, message);
    }


    public static <T> Result<T> failWithServerError() {
        return new Result<>(null, SERVER_ERROR.code, SERVER_ERROR.message);
    }

    public static <T> Result<T> failWithServerError(String message) {
        return new Result<>(null, SERVER_ERROR.code, message);
    }

    public static <T> Result<T> failWithTokenError(String message) {
        return new Result<>(null, TOKEN_ERROR.code, message);
    }

    public static <T> Result<T> failWithTokenError() {
        return new Result<>(null, TOKEN_ERROR.code, TOKEN_ERROR.message);
    }

    public static <T> Result<T> failWithLocked(String message) {
        return new Result<>(null, LOCKED.code, message);
    }

    public static <T> Result<T> failWithDisabled() {
        return new Result<>(null, DISABLED.code, DISABLED.message);
    }
    public static <T> Result<T> failWithDisabled(String message) {
        return new Result<>(null, DISABLED.code, message);
    }
    public static <T> Result<T> failWithNotFound() {
        return new Result<>(null, NOT_FOUND.code, NOT_FOUND.message);
    }
    public static <T> Result<T> failWithUnderReview() {
        return new Result<>(null, UNDER_REVIEW.code, UNDER_REVIEW.message);
    }

    public static <T> Result<T> with(T data, int code, String message) {
        return new Result<>(data, code, message);
    }

    @Getter
    @AllArgsConstructor
    public enum StatusCode {
        SUCCESS(200, "success"),
        WARN(600, "warn"),
        UN_AUTHORIZE(401, "unAuthorize"),
        REQUEST_BAD(400, "request args bad"),
        SERVER_ERROR(501, "Server Error"),
        TOKEN_ERROR(201, "Token Error"),
        DISABLED(3000, "disabled"),
        LOCKED(3001, "locked"),
        UNDER_REVIEW(3002, "account under review"),
        NOT_FOUND(4000, "account not found"),
        FAIL(500, "failed"),
        ;

        final int code;

        final String message;
    }

}

