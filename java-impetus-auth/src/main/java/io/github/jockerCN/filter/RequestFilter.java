package io.github.jockerCN.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface RequestFilter {

    String name();

    boolean supports(HttpServletRequest request);
}
