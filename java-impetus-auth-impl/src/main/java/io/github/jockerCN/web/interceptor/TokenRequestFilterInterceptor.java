package io.github.jockerCN.web.interceptor;

import io.github.jockerCN.access.RequestAuthorization;
import io.github.jockerCN.filter.interceptor.RequestFilterInterceptor;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.http.request.RequestInfo;
import io.github.jockerCN.token.TokenProcessException;
import io.github.jockerCN.token.process.TokenRecordProcess;
import io.github.jockerCN.type.TypeConvert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Order
@Slf4j
@SuppressWarnings("unused")
public class TokenRequestFilterInterceptor implements RequestFilterInterceptor {

    final RequestAuthorization<String> requestAuthorization = TypeConvert.cast(RequestAuthorization.getInstance());


    public TokenRequestFilterInterceptor() {
        log.info("### TokenRequestFilterInterceptor#init ###");
    }

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
        if (StringUtils.isBlank(accessToken)) {
            throw new TokenProcessException("token is invalid");
        }
        TokenRecordProcess.getInstance().validate(accessToken);

       /* final Set<String> groups =
                RequestContext.getRequestContext().userInfo().getGroups();

        if (!requestAuthorization.access(groups)) {
            throw new TokenProcessException("token is invalid");
        }*/

        return true;
    }
}
