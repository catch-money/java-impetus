package io.github.jockerCN;

import io.github.jockerCN.redis.RedisUtils;
import io.github.jockerCN.redis.RedissonUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.BaseConfig;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass({RedissonClient.class, StringRedisTemplate.class})
@EnableConfigurationProperties(DataRedisProperties.class)
public class JavaImpetusRedissonAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaImpetusRedissonAutoConfiguration.class);

    public JavaImpetusRedissonAutoConfiguration() {
        LOGGER.info("### JavaImpetusRedissonAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RedisUtils.class)
    public RedisUtils redisUtils() {
        LOGGER.info("### JavaImpetusRedissonAutoConfiguration#RedisUtils ###");
        return new RedisUtils();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnBean(DataRedisConnectionDetails.class)
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(DataRedisProperties properties,
                                         DataRedisConnectionDetails connectionDetails,
                                         ObjectProvider<Config> configs) {
        Config config = configs.getIfAvailable(() -> createConfig(properties, connectionDetails));
        LOGGER.info("### JavaImpetusRedissonAutoConfiguration#RedissonClient ###");
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(RedissonUtils.class)
    public RedissonUtils redissonUtils() {
        LOGGER.info("### JavaImpetusRedissonAutoConfiguration#RedissonUtils ###");
        return new RedissonUtils();
    }

    private static Config createConfig(DataRedisProperties properties, DataRedisConnectionDetails details) {
        Config config = new Config();
        SslBundle ssl = details.getSslBundle();
        boolean secure = Objects.nonNull(ssl)
                || Objects.nonNull(properties.getUrl()) && properties.getUrl().startsWith("rediss://");
        BaseConfig<?> server;
        var sentinel = details.getSentinel();
        var cluster = details.getCluster();
        var masterReplica = details.getMasterReplica();
        if (Objects.nonNull(sentinel)) {
            server = config.useSentinelServers()
                    .setMasterName(sentinel.getMaster())
                    .setDatabase(sentinel.getDatabase())
                    .setSentinelUsername(sentinel.getUsername())
                    .setSentinelPassword(sentinel.getPassword())
                    .addSentinelAddress(sentinel.getNodes().stream()
                            .map(node -> address(node.host(), node.port(), secure)).toArray(String[]::new));
        } else if (Objects.nonNull(cluster)) {
            server = config.useClusterServers().addNodeAddress(cluster.getNodes().stream()
                    .map(node -> address(node.host(), node.port(), secure)).toArray(String[]::new));
        } else if (Objects.nonNull(masterReplica)) {
            var nodes = masterReplica.getNodes();
            var master = nodes.getFirst();
            server = config.useMasterSlaveServers()
                    .setMasterAddress(address(master.host(), master.port(), secure))
                    .setDatabase(properties.getDatabase())
                    .addSlaveAddress(nodes.stream().skip(1)
                            .map(node -> address(node.host(), node.port(), secure)).toArray(String[]::new));
        } else {
            var standalone = Objects.requireNonNull(details.getStandalone(), "Redis standalone connection details");
            server = config.useSingleServer()
                    .setAddress(address(standalone.getHost(), standalone.getPort(), secure))
                    .setDatabase(standalone.getDatabase());
        }
        config.setUsername(details.getUsername());
        config.setPassword(details.getPassword());
        server.setClientName(properties.getClientName());
        if (Objects.nonNull(properties.getTimeout())) {
            server.setTimeout(Math.toIntExact(properties.getTimeout().toMillis()));
        }
        if (Objects.nonNull(properties.getConnectTimeout())) {
            server.setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));
        }
        if (Objects.nonNull(ssl)) {
            config.setSslTrustManagerFactory(ssl.getManagers().getTrustManagerFactory());
            config.setSslKeyManagerFactory(ssl.getManagers().getKeyManagerFactory());
            config.setSslProtocols(ssl.getOptions().getEnabledProtocols());
            config.setSslCiphers(ssl.getOptions().getCiphers());
        }
        return config;
    }

    private static String address(String host, int port, boolean secure) {
        String hostname = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return (secure ? "rediss://" : "redis://") + hostname + ":" + port;
    }
}
