package io.github.jockerCN.config.url;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

@Data
@ConfigurationProperties("jocker-cn.auth.url.config")
public class DefaultAuthUrlConfig implements AuthUrlConfig {

    private Set<String> noAuthUrls = Sets.newHashSet();

    private Set<String> authUrls = Sets.newHashSet();

    private Map<String, Set<String>> authRoleUrls = Maps.newHashMap();

    private String cacheKeyPrefix = "auth:permission:info";

    private long localMaximumSize = 1000;

    private AuthUrlStorage storage = AuthUrlStorage.DB;

    @Value("${server.servlet.context-path}")
    private String contextPath = "/";

    @Override
    public String contextPath() {
        return contextPath;
    }

    @Override
    public Set<String> noAuthUrlSet() {
        return noAuthUrls;
    }


    @Override
    public Set<String> authUrlSet() {
        return authUrls;
    }

    @Override
    public Map<String, Set<String>> authRoleUrlMap() {
        return authRoleUrls;
    }


    @Override
    public String cacheKeyPrefix() {
        return cacheKeyPrefix;
    }

    @Override
    public long localMaximumSize() {
        return localMaximumSize;
    }

    @Override
    public AuthUrlStorage storage() {
        return storage;
    }
}