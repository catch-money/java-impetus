package io.github.jockerCN.web.interceptor;

import io.github.jockerCN.filter.interceptor.RequestFilterInterceptor;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.http.request.RequestInfo;
import io.github.jockerCN.token.process.TokenRecordProcess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Order
@Slf4j
@SuppressWarnings("unused")
public class TokenRequestFilterInterceptor implements RequestFilterInterceptor {

    @Override
    public String name() {
        return TokenRequestFilterInterceptor.class.getName();
    }

    @Override
    public boolean supports(HttpServletRequest request) {
        return !RequestContext.getRequestContext().isPublicRequest();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        RequestInfo requestInfo = RequestContext.getRequestContext();
        final String accessToken = requestInfo.accessToken();
        TokenRecordProcess.getInstance().validate(accessToken);
        return true;
    }
}
