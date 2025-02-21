package io.github.jockerCN.security.filter;

import io.github.jockerCN.filter.security.SecurityRequestFilter;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.http.request.RequestInfo;
import io.github.jockerCN.http.request.UserInfo;
import io.github.jockerCN.permissions.AuthUrlProcess;
import io.github.jockerCN.token.process.TokenRecordProcess;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Order(FilterOrder.SECOND_LAST_PRIORITY)
@Slf4j
public class TokenParseSecurityRequestFilter implements SecurityRequestFilter {

    @Override
    public String name() {
        return TokenParseSecurityRequestFilter.class.getName();
    }

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        RequestInfo requestInfo = RequestContext.getRequestContext();
        final String accessToken = requestInfo.accessToken();
        TokenRecordProcess.getInstance().validate(accessToken);
        UserInfo userInfo = requestInfo.userInfo();
        User user = new User(userInfo.getUserCode(), "", userInfo.getGroups().stream().map(groupId -> (GrantedAuthority) () -> groupId).collect(Collectors.toSet()));
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    @Override
    public boolean supports(HttpServletRequest request) {
        return !AuthUrlProcess.getInstance().isNoAuthUrl(request.getRequestURI());
    }
}
