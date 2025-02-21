package io.github.jockerCN.filter;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.filter.security.SecurityRequestFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public interface SecurityFilterManager {

    void doFilter(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException;

    static SecurityFilterManager defaultManager() {
        final Collection<SecurityRequestFilter> securityRequestFilters = SpringProvider.getBeans(SecurityRequestFilter.class);
        return new SecurityFilterManager() {
            final Logger LOGGER = LoggerFactory.getLogger(SecurityFilterManager.class);
            @Override
            public void doFilter(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                for (SecurityRequestFilter securityRequestFilter : securityRequestFilters) {
                    if (securityRequestFilter.supports(request)) {
                        LOGGER.info("###[RequestFilter] execute filter [{}]", securityRequestFilter.name());
                        securityRequestFilter.doFilterInternal(request, response);
                    }
                }
            }
        };
    }

}
