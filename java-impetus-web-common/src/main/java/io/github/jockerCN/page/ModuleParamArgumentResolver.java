package io.github.jockerCN.page;

import com.google.gson.JsonSyntaxException;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import io.github.jockerCN.json.GsonConfig;
import io.github.jockerCN.time.TimeFormatterTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ModuleParamArgumentResolver implements HandlerMethodArgumentResolver {

    private final Map<String, Class<? extends BaseQueryParam>> MODULE_PARAM_CLASS_MAP;

    public ModuleParamArgumentResolver() {
        Collection<PageMapper> pageMappers = SpringProvider.getBeans(PageMapper.class);
        if (CollectionUtils.isEmpty(pageMappers)) {
            MODULE_PARAM_CLASS_MAP = PageMapper.defaultEmptyPageMapper().getQueryParamClassMap();
        } else {
            MODULE_PARAM_CLASS_MAP = new HashMap<>(128);
            for (PageMapper pageMapper : pageMappers) {
                MODULE_PARAM_CLASS_MAP.putAll(pageMapper.getQueryParamClassMap());
            }
        }
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ModulePageParam.class)
                && BaseQueryParam.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Objects.requireNonNull(request);
        String module = request.getParameter("module");

        if (StringUtils.isBlank(module)) {
            throw new CustomerArgumentResolverException("Page query module parameter is required");
        }

        Class<? extends BaseQueryParam> paramClass = MODULE_PARAM_CLASS_MAP.get(module);
        if (Objects.isNull(paramClass)) {
            throw new CustomerArgumentResolverException("Invalid module parameter");
        }

        BaseQueryParam param = paramClass.getDeclaredConstructor().newInstance();

        ServletRequestParameterPropertyValues propertyValues = new ServletRequestParameterPropertyValues(request);
        WebDataBinder binder = new WebDataBinder(param);
        binder.registerCustomEditor(LocalDateTime.class, new LocalDateTimeEditor());
        binder.registerCustomEditor(LocalDate.class, new LocalDateEditor());
        binder.bind(propertyValues);
        return param;
    }


    public static class LocalDateTimeEditor extends PropertyEditorSupport {

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            if (text != null && !text.isEmpty()) {
                setValue(GsonConfig.stringToLocalDateTime(text));
            }
        }
    }


    public static class LocalDateEditor extends PropertyEditorSupport {

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            if (text != null && !text.isEmpty()) {
                try {
                    setValue(LocalDate.parse(text, TimeFormatterTemplate.FORMATTER_YMD));
                } catch (Exception e) {
                    throw new JsonSyntaxException(e);
                }

            }
        }
    }

}
