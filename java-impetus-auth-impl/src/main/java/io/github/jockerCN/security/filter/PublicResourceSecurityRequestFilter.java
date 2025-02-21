package io.github.jockerCN.security.filter;


import io.github.jockerCN.config.AuthUserInfoProperties;
import io.github.jockerCN.filter.security.SecurityRequestFilter;
import io.github.jockerCN.permissions.AuthUrlProcess;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.io.IOException;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Order(FilterOrder.THIRD_LAST_PRIORITY)
@Slf4j
public class PublicResourceSecurityRequestFilter implements SecurityRequestFilter {

    public PublicResourceSecurityRequestFilter() {
        log.info("### PublicResourceSecurityRequestFilter#init ###");
    }

    @Override
    public String name() {
        return PublicResourceSecurityRequestFilter.class.getName();
    }

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = new User(AuthUserInfoProperties.getInstance().getTempUserName(), "", Set.of());
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    @Override
    public boolean supports(HttpServletRequest request) {
        return AuthUrlProcess.getInstance().isNoAuthUrl(request.getRequestURI());
    }
}
