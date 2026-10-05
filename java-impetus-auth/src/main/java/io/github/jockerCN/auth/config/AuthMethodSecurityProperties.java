package io.github.jockerCN.auth.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Explicit adapter selection; the presence of Security never changes the mode. */
@ConfigurationProperties("java-impetus.auth.method-security")
public record AuthMethodSecurityProperties(
        @DefaultValue("NATIVE") Mode mode,
        @DefaultValue("250") int order) {
    public AuthMethodSecurityProperties {
        Objects.requireNonNull(mode, "mode");
    }

    public enum Mode { NATIVE, SECURITY, DISABLED }
}
