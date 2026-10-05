package io.github.jockerCN.web.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Request-local correlation, not an authentication token or a distributed tracing replacement. */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private final String headerName;
    private final String mdcKey;
    private final boolean trustIncoming;

    public RequestIdFilter(RequestIdProperties properties) {
        headerName = properties.getHeaderName();
        mdcKey = properties.getMdcKey();
        trustIncoming = properties.isTrustIncoming();
        if (Objects.isNull(headerName) || !HEADER_NAME.matcher(headerName).matches()
                || Objects.isNull(mdcKey) || mdcKey.isBlank()) {
            throw new IllegalArgumentException("Request ID requires a valid HTTP header name and a nonblank MDC key");
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                               @NonNull FilterChain chain) throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        String id = existing instanceof String value ? value : createId(request);
        request.setAttribute(REQUEST_ATTRIBUTE, id);
        if (!response.isCommitted()) {
            response.setHeader(headerName, id);
        }
        String previous = MDC.get(mdcKey);
        MDC.put(mdcKey, id);
        try {
            chain.doFilter(request, response);
        } finally {
            if (Objects.isNull(previous)) {
                MDC.remove(mdcKey);
            } else {
                MDC.put(mdcKey, previous);
            }
        }
    }

    private String createId(HttpServletRequest request) {
        String supplied = request.getHeader(headerName);
        return trustIncoming && Objects.nonNull(supplied) && SAFE_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
    }
}
