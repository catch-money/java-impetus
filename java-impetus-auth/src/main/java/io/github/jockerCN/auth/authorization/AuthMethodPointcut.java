package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.annotation.AuthAccess;
import io.github.jockerCN.auth.annotation.UseAuthPolicy;
import java.lang.annotation.Annotation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;

/** Only our declarations. Security-only and undeclared methods never enter the Auth adapter. */
public final class AuthMethodPointcut extends ComposablePointcut {
    public AuthMethodPointcut() {
        super(classOrMethod(AuthAccess.class));
        union(classOrMethod(UseAuthPolicy.class));
    }

    private static Pointcut classOrMethod(Class<? extends Annotation> annotation) {
        return Pointcuts.union(new AnnotationMatchingPointcut(annotation, true),
                new AnnotationMatchingPointcut(null, annotation, true));
    }
}
