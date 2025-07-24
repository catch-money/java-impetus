package io.github.jockerCN.log;


import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Aspect
@Configuration
public class LogAspectController {


    private static final Logger logger = LoggerFactory.getLogger(LogAspectController.class);


    @Pointcut(value = "@annotation(io.github.jockerCN.log.AutoLog)")
    public void pointcut() {

    }


    @Before(value = "pointcut()")
    public void autoLogger(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AutoLog autoLogAnnotation = method.getAnnotation(AutoLog.class);
        logger.info("[{}] ARGS: {}", autoLogAnnotation.value(),  joinPoint.getArgs());
    }
}
