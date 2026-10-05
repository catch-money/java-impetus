package io.github.jockerCN.log;

import java.lang.reflect.Method;
import java.time.Duration;

/** Invocation-local values. A null result is valid; failure distinguishes thrown calls. */
public record AutoLogContext(Object target, Method method, Object[] arguments,
                             Object result, Throwable failure, Duration elapsed) {
}
