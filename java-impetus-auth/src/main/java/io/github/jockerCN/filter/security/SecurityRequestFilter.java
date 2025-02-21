package io.github.jockerCN.filter.security;

import io.github.jockerCN.filter.RequestFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface SecurityRequestFilter extends RequestFilter {

    void doFilterInternal(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;

}
