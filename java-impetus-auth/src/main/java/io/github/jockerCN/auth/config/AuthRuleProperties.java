package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.authorization.AuthAccessRequirement.Access;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Ordered, startup-bound declarations, not a configuration hot-reload or workflow DSL. */
@ConfigurationProperties("java-impetus.auth")
public record AuthRuleProperties(@DefaultValue("AUTHENTICATED") Access defaultAccess,
                                  @DefaultValue List<RequestRule> rules) {
    public record RequestRule(List<String> paths, @DefaultValue List<String> methods,
                              @DefaultValue("AUTHENTICATED") Access access,
                              @DefaultValue Authorities roles, @DefaultValue Authorities permissions,
                              String policy) { }
    public record Authorities(@DefaultValue List<String> all, @DefaultValue List<String> any) { }
}
