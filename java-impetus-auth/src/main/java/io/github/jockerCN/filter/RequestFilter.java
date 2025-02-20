package io.github.jockerCN.filter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface RequestFilter {

    String name();

    void doFilterInternal(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;

    boolean supports(HttpServletRequest request);
}
