package io.github.jockerCN.log;

import io.github.jockerCN.JavaImpetusSpringAutoConfiguration;
import io.github.jockerCN.common.SpringProvider;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoConfiguration(after = JavaImpetusSpringAutoConfiguration.class)
@ConditionalOnClass({Aspect.class, EnableAspectJAutoProxy.class})
@EnableAspectJAutoProxy
public class AutoLogConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoLogConfiguration.class);

    public AutoLogConfiguration() {
        LOGGER.info("### AutoLogConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(LogAspectController.class)
    public LogAspectController logAspectController(SpringProvider provider) {
        LOGGER.info("### AutoLogConfiguration#LogAspectController ###");
        // The dependency ensures the shared static provider is initialized before the aspect.
        return new LogAspectController();
    }
}
