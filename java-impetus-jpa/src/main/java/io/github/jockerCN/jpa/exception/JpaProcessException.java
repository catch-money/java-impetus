package io.github.jockerCN.jpa.exception;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class JpaProcessException extends RuntimeException{

    public JpaProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    public JpaProcessException(String message) {
        super(message);
    }
}
