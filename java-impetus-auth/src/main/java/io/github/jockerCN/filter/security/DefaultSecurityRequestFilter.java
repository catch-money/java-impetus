package io.github.jockerCN.filter.security;

import io.github.jockerCN.http.HttpRequestFilter;
import io.github.jockerCN.http.HttpResponseFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultSecurityRequestFilter implements SecurityRequestFilter, HttpRequestFilter, HttpResponseFilter {

    public DefaultSecurityRequestFilter() {
        log.info("### DefaultRequestFilter#init ###");
    }

    @Override
    public String name() {
        return DefaultSecurityRequestFilter.class.getName();
    }

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response) {
        log.info("### DefaultRequestFilter#doFilterInternal ###");
        setRequestInfo(request);
    }

    @Override
    public boolean supports(HttpServletRequest request) {
        return true;
    }
}
