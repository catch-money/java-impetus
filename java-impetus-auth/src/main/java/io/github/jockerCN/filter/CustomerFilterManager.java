package io.github.jockerCN.filter;

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

public interface CustomerFilterManager {

    void doFilter(Collection<RequestFilter> requestFilters, final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException;

    static CustomerFilterManager defaultManager() {
        return new CustomerFilterManager() {
            final Logger LOGGER = LoggerFactory.getLogger(CustomerFilterManager.class);
            @Override
            public void doFilter(Collection<RequestFilter> requestFilters, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                for (RequestFilter requestFilter : requestFilters) {
                    if (requestFilter.supports(request)) {
                        LOGGER.info("###[RequestFilter] execute filter [{}]", requestFilter.name());
                        requestFilter.doFilterInternal(request, response);
                    }
                }
            }
        };
    }

}
