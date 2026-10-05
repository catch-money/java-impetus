package io.github.jockerCN.jpa.query.operator;

/**
 * Describes the expected input type of a query operator or SQL function.
 * This is not a blanket runtime check; actual SQL support depends on the database.
 * {@link AllType} represents an unrestricted input type.
 *
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface JavaTypeSupport {

    Class<?> supportType();
}
