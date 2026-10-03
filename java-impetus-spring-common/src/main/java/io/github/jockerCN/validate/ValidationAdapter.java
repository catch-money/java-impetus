package io.github.jockerCN.validate;

import io.github.jockerCN.Result;
import io.github.jockerCN.annotation.Validator;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@FunctionalInterface
public interface ValidationAdapter {

    Result<?> validate(Object o, Validator commonValidator);

    /** Check annotation configuration once before values are validated. */
    default void validateConfiguration(Validator annotation) {
    }
}
