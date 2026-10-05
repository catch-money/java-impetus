package io.github.jockerCN.common;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.Optional;

/** Binds an Environment prefix to a value object without registering a bean. */
public final class SpringConfigurationUtils {

    private SpringConfigurationUtils() {
    }

    public static <T> Optional<T> bind(String prefix, Class<T> type) {
        BindResult<T> result = Binder.get(SpringProvider.getEnvironment()).bind(prefix, Bindable.of(type));
        return result.isBound() ? Optional.of(result.get()) : Optional.empty();
    }

    public static <T> T bindRequired(String prefix, Class<T> type) {
        return bind(prefix, type).orElseThrow(
                () -> new IllegalStateException("No configuration bound under '" + prefix + "'"));
    }
}
