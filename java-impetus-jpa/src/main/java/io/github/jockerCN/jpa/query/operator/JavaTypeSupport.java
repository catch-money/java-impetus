package io.github.jockerCN.jpa.query.operator;

/**
 * Declares the Java type accepted by a query operator or SQL function.
 * {@link AllType} represents an unrestricted input type.
 *
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface JavaTypeSupport {

    Class<?> supportType();
}
