package io.github.jockerCN.dao;

import com.google.common.base.Strings;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.http.request.RequestInfo;
import io.github.jockerCN.token.TokenWrapper;


/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class EntityUtils {

    public static UserLoginInfo creatorUserLoginInfoByTokenWrapper(final TokenWrapper tokenWrapper) {
        RequestInfo requestContext = RequestContext.getRequestContext();
        return UserLoginInfo.builder()
                .username(tokenWrapper.tokenInfo().getUsername())
                .userCode(tokenWrapper.tokenInfo().getUserCode())
                .accessToken(tokenWrapper.tokenInfo().getToken())
                .refreshToken(Strings.nullToEmpty(tokenWrapper.tokenInfo().getRefreshToken()))
                .loginTime(tokenWrapper.tokenInfo().getCreateTime())
                .expirationTime(tokenWrapper.tokenInfo().getExpireTime())
                .refreshExpirationTime(tokenWrapper.tokenInfo().getRefreshExpiresIn())
                .lastRefreshTime(tokenWrapper.tokenInfo().getCreateTime())
                .tokenExpiryStrategy(tokenWrapper.tokenInfo().getExpiryStrategy())
                .loginMethod(Strings.nullToEmpty(requestContext.loginMethod()))
                .deviceInfo(Strings.nullToEmpty(requestContext.deviceInfo()))
                .ipAddress(Strings.nullToEmpty(requestContext.ipAddress()))
                .userAgent(Strings.nullToEmpty(requestContext.userAgent()))
                .build();
    }
}
