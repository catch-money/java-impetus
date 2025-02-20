package io.github.jockerCN.filter;

import io.github.jockerCN.http.HttpRequestFilter;
import io.github.jockerCN.http.HttpResponseFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultRequestFilter implements RequestFilter, HttpRequestFilter, HttpResponseFilter {

    public DefaultRequestFilter() {
        log.info("### DefaultRequestFilter#init ###");
    }

    @Override
    public String name() {
        return DefaultRequestFilter.class.getName();
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
