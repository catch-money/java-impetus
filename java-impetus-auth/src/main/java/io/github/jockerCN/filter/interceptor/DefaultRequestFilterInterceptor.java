package io.github.jockerCN.filter.interceptor;

import io.github.jockerCN.filter.FilterOrder;
import io.github.jockerCN.http.HttpRequestFilter;
import io.github.jockerCN.http.HttpResponseFilter;
import io.github.jockerCN.permissions.AuthUrlProcess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Order(FilterOrder.SECOND_LAST_PRIORITY)
@Slf4j
public class DefaultRequestFilterInterceptor implements RequestFilterInterceptor, HttpRequestFilter, HttpResponseFilter {


    @Override
    public String name() {
        return DefaultRequestFilterInterceptor.class.getName();
    }

    @Override
    public boolean supports(HttpServletRequest request) {
        return true;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        setRequestInfo(request);
        AuthUrlProcess.getInstance().isNoAuthUrl(request.getRequestURI());
        return true;
    }
}
