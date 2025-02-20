package io.github.jockerCN.security;


import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.filter.CustomerFilterManager;
import io.github.jockerCN.filter.RequestFilter;
import io.github.jockerCN.security.exception.SecurityFilterException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Order
@Component
@DependsOn("springProvider")
public class DefaultFilter extends OncePerRequestFilter {

    private final Collection<RequestFilter> filters = SpringProvider.getBeans(RequestFilter.class);

    private final CustomerFilterManager filterManager = SpringProvider.getBeanOrDefault(CustomerFilterManager.class, CustomerFilterManager.defaultManager());

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            filterManager.doFilter(filters, request, response);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            SecurityFilterException.getInstance().doException(e, request, response);
        }
    }
}
