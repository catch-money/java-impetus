package io.github.jockerCN.security;

import lombok.extern.slf4j.Slf4j;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class DefaultSpringSecurityConfigManager extends AbstractSpringSecurityConfigManager{

    public DefaultSpringSecurityConfigManager() {
        log.info("### DefaultSpringSecurityConfigManager#init ###");
    }
}
