package io.github.jockerCN.filter;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.filter.interceptor.RequestFilterInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collection;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public interface InterceptorFilterManager {

    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;

    default void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
    }

    default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
    }

    static InterceptorFilterManager defaultManager() {

        final Collection<RequestFilterInterceptor> requestFilterInterceptors = SpringProvider.getBeans(RequestFilterInterceptor.class);
        return new InterceptorFilterManager() {

            final ThreadLocal<RequestFilterInterceptor> requestFilterInterceptorThreadLocal = new InheritableThreadLocal<>();
            final Logger LOGGER = LoggerFactory.getLogger(InterceptorFilterManager.class);
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                for (RequestFilterInterceptor requestFilterInterceptor : requestFilterInterceptors) {
                    if (requestFilterInterceptor.supports(request)) {
                        LOGGER.info("###[RequestFilterInterceptor#preHandle] execute filter [{}]", requestFilterInterceptor.name());
                        requestFilterInterceptorThreadLocal.set(requestFilterInterceptor);
                        return requestFilterInterceptor.preHandle(request, response, handler);
                    }
                }
                return false;
            }

            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
                RequestFilterInterceptor requestFilterInterceptor = requestFilterInterceptorThreadLocal.get();
                if (Objects.nonNull(requestFilterInterceptor)) {
                    LOGGER.info("###[RequestFilterInterceptor#postHandle] execute filter [{}]", requestFilterInterceptor.name());
                    requestFilterInterceptor.postHandle(request, response, handler, modelAndView);
                }
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
                RequestFilterInterceptor requestFilterInterceptor = requestFilterInterceptorThreadLocal.get();
                if (Objects.nonNull(requestFilterInterceptor)) {
                    LOGGER.info("###[RequestFilterInterceptor#afterCompletion] execute filter [{}]", requestFilterInterceptor.name());
                    requestFilterInterceptor.afterCompletion(request, response, handler, ex);
                }
                requestFilterInterceptorThreadLocal.remove();
            }
        };
    }

}
