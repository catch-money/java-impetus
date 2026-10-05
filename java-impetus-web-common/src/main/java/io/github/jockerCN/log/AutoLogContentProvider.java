package io.github.jockerCN.log;

/** Implement as a Spring bean; do not retain invocation context or sensitive data. */
@FunctionalInterface
public interface AutoLogContentProvider {

    Object content(AutoLogContext context);
}
