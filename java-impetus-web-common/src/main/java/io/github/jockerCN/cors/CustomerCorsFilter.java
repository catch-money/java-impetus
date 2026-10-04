package io.github.jockerCN.cors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class CustomerCorsFilter extends CorsFilter {

    public CustomerCorsFilter(CorsConfigurationSource source) {
        super(source);
    }
}
