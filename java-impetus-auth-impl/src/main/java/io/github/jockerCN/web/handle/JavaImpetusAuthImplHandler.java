package io.github.jockerCN.web.handle;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.exception.FilterException;
import io.github.jockerCN.filter.InterceptorFilterManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class JavaImpetusAuthImplHandler implements HandlerInterceptor {

    final FilterException filterException = FilterException.getInstance();

    public JavaImpetusAuthImplHandler() {
        log.info("### JavaImpetusAuthImplHandler#init ###");
    }

    final InterceptorFilterManager interceptorFilterManager = SpringProvider.getBeanOrDefault(InterceptorFilterManager.class, InterceptorFilterManager.defaultManager());

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        try {
            return interceptorFilterManager.preHandle(request, response, handler);
        } catch (Exception e) {
            filterException.doException(e, request, response);
            return false;
        }
    }


    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        interceptorFilterManager.postHandle(request, response, handler, modelAndView);
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) throws Exception {
        interceptorFilterManager.afterCompletion(request, response, handler, ex);
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
