package io.github.jockerCN.security;


import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.exception.FilterException;
import io.github.jockerCN.filter.SecurityFilterManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Order
public class DefaultFilter extends OncePerRequestFilter {

    private final SecurityFilterManager filterManager = SpringProvider.getBeanOrDefault(SecurityFilterManager.class, SecurityFilterManager.defaultManager());

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            filterManager.doFilter(request, response);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            FilterException.getInstance().doException(e, request, response);
        }
    }
}
