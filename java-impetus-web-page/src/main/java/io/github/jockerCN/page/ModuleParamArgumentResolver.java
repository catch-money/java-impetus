package io.github.jockerCN.page;

import io.github.jockerCN.jpa.paging.PageParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;

/** Routes and binds one fresh query parameter using MVC's own binder and conversion service. */
public class ModuleParamArgumentResolver implements HandlerMethodArgumentResolver {

    private final Map<String, Class<? extends PageParam>> modules;

    public ModuleParamArgumentResolver(Map<String, Class<? extends PageParam>> modules) {
        this.modules = Map.copyOf(modules);
    }

    public Class<? extends PageParam> getQueryParamType(String module) {
        if (Objects.isNull(module) || module.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page query module parameter is required");
        }
        Class<? extends PageParam> type = modules.get(module);
        if (Objects.isNull(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid module parameter");
        }
        return type;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ModulePageParam.class)
                && PageParam.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Class<? extends PageParam> type = getQueryParamType(Objects.requireNonNull(request).getParameter("module"));
        if (!parameter.getParameterType().isAssignableFrom(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module query parameter type does not match the endpoint");
        }
        PageParam queryParam = BeanUtils.instantiateClass(type);
        WebDataBinder binder = Objects.requireNonNull(binderFactory).createBinder(webRequest, queryParam, "queryParam");
        binder.bind(new ServletRequestParameterPropertyValues(request));
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            Object[] hints = ValidationAnnotationUtils.determineValidationHints(annotation);
            if (Objects.nonNull(hints)) {
                binder.validate(hints);
                break;
            }
        }
        if (binder.getBindingResult().hasErrors()) {
            throw new BindException(binder.getBindingResult());
        }
        return queryParam;
    }
}
