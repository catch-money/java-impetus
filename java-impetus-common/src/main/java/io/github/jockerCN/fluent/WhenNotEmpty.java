package io.github.jockerCN.fluent;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class WhenNotEmpty<T> extends WhenOperator<T> {

    public WhenNotEmpty(T t) {
        super(t, Objects::isNull);
        Predicate<T> predicate = this.predicate;
        if (t instanceof Array) {
            predicate = (arr) -> ArrayUtils.isNotEmpty((Object[]) arr);
        } else if (t instanceof Collection) {
            predicate = (coll) -> CollectionUtils.isNotEmpty((Collection<?>) coll);
        } else if (t instanceof Map) {
            predicate = (coll) -> MapUtils.isNotEmpty((Map<?, ?>) coll);
        }
        this.predicate = predicate;
    }

    static <T> WhenNotEmpty<T> bind(T t) {
        return new WhenNotEmpty<>(t) {
        };
    }
}
