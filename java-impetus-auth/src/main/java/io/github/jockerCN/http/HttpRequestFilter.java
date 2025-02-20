package io.github.jockerCN.http;

import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.http.request.RequestHeaderProperties;
import io.github.jockerCN.http.request.RequestInfo;
import io.github.jockerCN.http.request.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface HttpRequestFilter {


    default void setRequestInfo(final HttpServletRequest request) {
        RequestHeaderProperties headerProperties = RequestHeaderProperties.getInstance();

        String remoteAddr = request.getRemoteAddr();
        String xRealIP = request.getHeader(headerProperties.getXRealIP());
        String xForwardedIP = request.getHeader(headerProperties.getXForwardedForKey());

        if (StringUtils.isNotBlank(xForwardedIP)) {
            remoteAddr = StringUtils.split(xForwardedIP, ",")[0];
        } else if (StringUtils.isNotBlank(xRealIP)) {
            remoteAddr = xRealIP;
        }

        var requestInfo = new RequestInfo(
                new UserInfo(),
                request.getRequestURI(),
                request.getHeader(headerProperties.getLoginMethodKey()),
                request.getHeader(headerProperties.getDeviceInfoKey()),
                request.getHeader(headerProperties.getAccessTokenKey()),
                request.getHeader(headerProperties.getRefreshTokenKey()),
                remoteAddr,
                xRealIP,
                xForwardedIP,
                request.getHeader(headerProperties.getUserAgentKey())
        );
        RequestContext.setRequestContext(requestInfo);
    }
}
