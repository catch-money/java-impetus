package io.github.jockerCN.token;

import java.io.Serial;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public class TokenProcessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TokenProcessException() {
    }

    public TokenProcessException(String message) {
        super(message);
    }

    public TokenProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    public TokenProcessException(Throwable cause) {
        super(cause);
    }

    public TokenProcessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
