package io.github.jockerCN.exception;

import io.github.jockerCN.Result;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Objects;

/** MVC fallback: application-specific advice should have a higher priority. */
@Order
@RestControllerAdvice
public class GlobalExceptionController extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionController.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception exception, Object body,
                                                             @NonNull HttpHeaders headers, @NonNull HttpStatusCode status,
                                                             @NonNull WebRequest request) {
        String message = publicMessage(exception, status);
        log(exception, status, request);
        // Delegate response-committed checks and preserve native HTTP headers (e.g. Allow).
        return super.handleExceptionInternal(exception, Result.with(null, status.value(), message),
                headers, status, request);
    }

    @ExceptionHandler({BindException.class, CustomerArgumentResolverException.class,
            ConstraintViolationException.class})
    public ResponseEntity<Object> handleInvalidArguments(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            return handleExceptionInternal(exception, null, errorResponse.getHeaders(),
                    errorResponse.getStatusCode(), request);
        }
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(
                exception.getClass(), ResponseStatus.class);
        HttpStatus status = Objects.isNull(responseStatus) ? HttpStatus.INTERNAL_SERVER_ERROR : responseStatus.code();
        return handleExceptionInternal(exception, null, new HttpHeaders(), status, request);
    }

    private static String publicMessage(Exception exception, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return reason(status);
        }
        if (exception instanceof BindException bind) {
            return bind.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage)
                    .filter(Objects::nonNull).findFirst().orElse(reason(status));
        }
        if (exception instanceof HandlerMethodValidationException validation) {
            return validation.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage)
                    .filter(Objects::nonNull).findFirst().orElse(reason(status));
        }
        if (exception instanceof ConstraintViolationException validation) {
            return validation.getConstraintViolations().stream().map(violation -> violation.getMessage())
                    .filter(Objects::nonNull).sorted().findFirst().orElse(reason(status));
        }
        if (exception instanceof CustomerArgumentResolverException) {
            return Objects.nonNull(exception.getMessage()) ? exception.getMessage() : reason(status);
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "Malformed request body";
        }
        return reason(status);
    }

    private static String reason(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return Objects.isNull(resolved) ? "Request failed" : resolved.getReasonPhrase();
    }

    private static void log(Exception exception, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            LOGGER.error("Request failed [{}] {}", status.value(), request.getDescription(false), exception);
        } else {
            LOGGER.warn("Request rejected [{}] {} ({})", status.value(), request.getDescription(false),
                    exception.getClass().getSimpleName());
        }
    }
}
