package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.config.AuthRuleProperties;
import io.github.jockerCN.auth.policy.PolicyRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import org.springframework.util.AntPathMatcher;

/**
 * Ordered first-match rules over an application-normalized path and method. No Servlet dependency,
 * global scanning, HTTP interception or request cache. Patterns and policy names are fixed at startup.
 */
public final class AuthRequestRules {
    private final List<BiPredicate<String, String>> matches;
    private final List<AuthAccessRule> rules;
    private final AuthAccessRule fallback;

    public AuthRequestRules(AuthRuleProperties properties, PolicyRegistry policies) {
        Objects.requireNonNull(properties, "properties");
        var matcher = new AntPathMatcher();
        var compiledMatches = new ArrayList<BiPredicate<String, String>>();
        var compiledRules = new ArrayList<AuthAccessRule>();
        for (var declaration : properties.rules()) {
            List<String> paths = List.copyOf(declaration.paths());
            if (paths.isEmpty() || paths.stream().anyMatch(path -> !path.startsWith("/") || path.contains("{")
                    || path.contains("}") || path.contains("#") || path.contains("\\")))
                throw new IllegalArgumentException("request paths must be absolute Ant patterns without regex variables");
            Set<String> methods = Set.copyOf(declaration.methods().stream().map(AuthRequestRules::method).toList());
            var authorities = new ArrayList<AuthorityRequirement>();
            add(authorities, AuthorityRequirement.Kind.ROLE, declaration.roles());
            add(authorities, AuthorityRequirement.Kind.PERMISSION, declaration.permissions());
            List<String> required = Objects.isNull(declaration.policy()) ? List.of() : List.of(declaration.policy());
            required.forEach(policies::get);
            compiledRules.add(new AuthAccessRule(new AuthAccessRequirement(declaration.access(), authorities), required));
            compiledMatches.add((path, method) -> (methods.isEmpty() || methods.contains(method))
                    && paths.stream().anyMatch(pattern -> matcher.match(pattern, path)));
        }
        this.matches = List.copyOf(compiledMatches);
        this.rules = List.copyOf(compiledRules);
        this.fallback = new AuthAccessRule(new AuthAccessRequirement(properties.defaultAccess(), List.of()), List.of());
    }

    public AuthAccessRule resolve(String path, String method) {
        if (Objects.isNull(path) || !path.startsWith("/") || path.contains("?") || path.contains("#") || path.contains("\\"))
            throw new IllegalArgumentException("supply the application's normalized absolute path without a query string");
        String verb = method(method);
        for (int index = 0; index < matches.size(); index++)
            if (matches.get(index).test(path, verb)) return rules.get(index);
        return fallback;
    }

    private static String method(String value) {
        if (Objects.isNull(value) || !value.matches("[A-Za-z][A-Za-z0-9!#$%&'*+.^_`|~-]*"))
            throw new IllegalArgumentException("method must be a nonblank HTTP token");
        return value.toUpperCase(Locale.ROOT);
    }

    private static void add(List<AuthorityRequirement> target, AuthorityRequirement.Kind kind,
                            AuthRuleProperties.Authorities values) {
        if (Objects.isNull(values)) return;
        if (!values.all().isEmpty()) target.add(new AuthorityRequirement(kind, AuthorityRequirement.Match.ALL, Set.copyOf(values.all())));
        if (!values.any().isEmpty()) target.add(new AuthorityRequirement(kind, AuthorityRequirement.Match.ANY, Set.copyOf(values.any())));
    }
}
