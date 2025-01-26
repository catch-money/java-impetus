package io.github.jockerCN.beans;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BeanConfiguration {

    public BeanConfiguration() {
      log.info("### BeanConfiguration#init ###");
    }
}