package io.github.jockerCN.fluent;

import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public final class DefaultValue {

    public static final DefaultValue DEFAULT = new DefaultValue();

    private DefaultValue() {
    }

    public static boolean isDefault(Object o) {
        return o instanceof DefaultValue;
    }
}
