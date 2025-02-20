package io.github.jockerCN.http;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface HttpResponseFilter {


    default void responseJsonConfigSet(HttpServletResponse response) {
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
    }
}
