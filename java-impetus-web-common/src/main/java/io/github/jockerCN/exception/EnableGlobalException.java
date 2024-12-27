package io.github.jockerCN.exception;


import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({GlobalExceptionController.class})
public @interface EnableGlobalException {
}
