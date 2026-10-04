package io.github.jockerCN.page;

import io.github.jockerCN.jpa.query.model.QueryPair;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

/** Two repeated parameters or a single comma-separated pair, using the declared endpoint type. */
public final class QueryPairConverter implements GenericConverter {

    private final ConversionService conversionService;

    public QueryPairConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Set.of(new ConvertiblePair(String.class, QueryPair.class),
                new ConvertiblePair(String[].class, QueryPair.class));
    }

    @Override
    public Object convert(Object source, @NonNull TypeDescriptor sourceType, @NonNull TypeDescriptor targetType) {
        if (Objects.isNull(source)) {
            return null;
        }
        String[] values = source instanceof String text ? StringUtils.commaDelimitedListToStringArray(text)
                : (String[]) source;
        if (values.length != 2) {
            throw new IllegalArgumentException("QueryPair requires exactly two values");
        }
        var endpointType = targetType.getResolvableType().getGeneric(0);
        Class<?> endpointClass = endpointType.resolve();
        if (Objects.isNull(endpointClass) || !Comparable.class.isAssignableFrom(endpointClass)) {
            throw new IllegalArgumentException("QueryPair requires a concrete Comparable endpoint type");
        }
        TypeDescriptor endpoint = new TypeDescriptor(endpointType, endpointClass, targetType.getAnnotations());
        return new QueryPair<>((Comparable<?>) conversionService.convert(values[0], TypeDescriptor.valueOf(String.class), endpoint),
                (Comparable<?>) conversionService.convert(values[1], TypeDescriptor.valueOf(String.class), endpoint));
    }
}
