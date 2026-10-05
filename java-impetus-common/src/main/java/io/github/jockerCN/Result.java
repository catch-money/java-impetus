package io.github.jockerCN;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static io.github.jockerCN.Result.StatusCode.*;


/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
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

    public static <T> Result<T> warn(T data, String message) {
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
        return new Result<>(null, INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR.message);
    }

    public static <T> Result<T> failEmpty() {
        return new Result<>(null, INTERNAL_SERVER_ERROR.code, "");
    }

    public static <T> Result<T> failWithMsg(String message) {
        return new Result<>(null, INTERNAL_SERVER_ERROR.code, message);
    }

    public static <T> Result<T> failWithUNAuth() {
        return new Result<>(null, UNAUTHORIZED.code, UNAUTHORIZED.message);
    }

    public static <T> Result<T> failWithNoPermission() {
        return new Result<>(null, FORBIDDEN.code, FORBIDDEN.message);
    }

    public static <T> Result<T> failWithNoPermission(String message) {
        return new Result<>(null, FORBIDDEN.code, message);
    }


    public static <T> Result<T> failWithUNAuth(String message) {
        return new Result<>(null, UNAUTHORIZED.code, message);
    }


    public static <T> Result<T> failWithServerError() {
        return new Result<>(null, INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR.message);
    }

    public static <T> Result<T> failWithServerError(String message) {
        return new Result<>(null, INTERNAL_SERVER_ERROR.code, message);
    }

    public static <T> Result<T> failWithTokenError(String message) {
        return new Result<>(null, UNAUTHORIZED.code, message);
    }

    public static <T> Result<T> failWithTokenError() {
        return new Result<>(null, UNAUTHORIZED.code, UNAUTHORIZED.message);
    }

    public static <T> Result<T> failWithLocked(String message) {
        return new Result<>(null, LOCKED.code, message);
    }

    public static <T> Result<T> failWithLocked() {
        return new Result<>(null, LOCKED.code, LOCKED.message);
    }

    public static <T> Result<T> failWithDisabled() {
        return new Result<>(null, DISABLED.code, DISABLED.message);
    }

    public static <T> Result<T> failWithDisabled(String message) {
        return new Result<>(null, DISABLED.code, message);
    }

    public static <T> Result<T> failWithNotFound() {
        return new Result<>(null, RESOURCE_NOT_FOUND.code, RESOURCE_NOT_FOUND.message);
    }

    public static <T> Result<T> failWithNotFound(String message) {
        return new Result<>(null, RESOURCE_NOT_FOUND.code, message);
    }

    public static <T> Result<T> failWithUnderReview() {
        return new Result<>(null, UNDER_REVIEW.code, UNDER_REVIEW.message);
    }

    public static <T> Result<T> with(T data, int code, String message) {
        return new Result<>(data, code, message);
    }

    public static <T> Result<T> with(StatusCode status) {
        return with(null, status);
    }

    public static <T> Result<T> with(T data, StatusCode status) {
        return new Result<>(data, status.code, status.message);
    }

    public static <T> Result<T> with(T data, StatusCode status, String message) {
        return new Result<>(data, status.code, message);
    }

    /**
     * Codes in the response body. They do not change the actual HTTP response status.
     * {@link Result#isOk()} means code 200 only. Business-specific states remain
     * separate from the standard HTTP-style error codes.
     */
    @Getter
    @AllArgsConstructor
    public enum StatusCode {
        SUCCESS(200, "success"),
        WARN(600, "warn"),
        DISABLED(3000, "disabled"),
        LOCKED(3001, "locked"),
        UNDER_REVIEW(3002, "account under review"),

        BAD_REQUEST(400, "Bad Request"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        RESOURCE_NOT_FOUND(404, "Not Found"),
        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        REQUEST_TIMEOUT(408, "Request Timeout"),
        CONFLICT(409, "Conflict"),
        GONE(410, "Gone"),
        PAYLOAD_TOO_LARGE(413, "Content Too Large"),
        UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
        UNPROCESSABLE_CONTENT(422, "Unprocessable Content"),
        TOO_MANY_REQUESTS(429, "Too Many Requests"),
        INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
        BAD_GATEWAY(502, "Bad Gateway"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable"),
        GATEWAY_TIMEOUT(504, "Gateway Timeout"),
        ;

        final int code;

        final String message;
    }

}

