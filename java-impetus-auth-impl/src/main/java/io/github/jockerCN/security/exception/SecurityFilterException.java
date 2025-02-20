package io.github.jockerCN.security.exception;

import io.github.jockerCN.Result;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.http.HttpResponseFilter;
import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.token.TokenProcessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

public interface SecurityFilterException {


    void doException(Exception e, HttpServletRequest request, HttpServletResponse response);


    static SecurityFilterException getInstance() {
        return SpringProvider.getBeanOrDefault(SecurityFilterException.class, defaultSecurityFilterException);
    }

    DefaultSecurityFilterException defaultSecurityFilterException = new DefaultSecurityFilterException();

    @Slf4j
    class DefaultSecurityFilterException implements SecurityFilterException, HttpResponseFilter {

        @Override
        public void doException(Exception e, HttpServletRequest request, HttpServletResponse response) {
            responseJsonConfigSet(response);
            //权限校验错误
            Result<Void> result = Result.failWithMsg(String.format("%s ", e.getMessage()));
            //token
            if (e instanceof TokenProcessException tokenException) {
                result = Result.failWithTokenError(tokenException.getMessage());
            }else if (e instanceof SecurityException securityException) {
                result = Result.failWithUNAuth(securityException.getMessage());
            }
            log.warn(e.getMessage(), e);

            try {
                response.getWriter().write(GsonUtils.toJson(result));
            } catch (IOException ex) {
                log.error("### DefaultSecurityFilterException response error ", ex);
            }
        }
    }
}
