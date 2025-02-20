package io.github.jockerCN.permissions;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import io.github.jockerCN.cache.CacheManager;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.config.url.AuthUrlConfig;
import io.github.jockerCN.dao.DaoUtils;
import io.github.jockerCN.dao.Permission;
import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.redis.RedisUtils;
import io.github.jockerCN.stream.StreamUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface AuthUrlProcess {


    void refreshPermissions();

    Set<String> getPublicPermissionsURI();

    default boolean isNoAuthUrl(String uri) {
        for (String publicURI : getPublicPermissionsURI()) {
            if (SpringUtils.antPathMatch(publicURI, uri)) {
                return true;
            }
        }
        return false;
    }

    static AuthUrlProcess getInstance() {
        return SpringProvider.getBeanOrDefault(AuthUrlProcess.class, new DefaultAuthUrlProcess());
    }


    class DefaultAuthUrlProcess implements AuthUrlProcess {

        private final Cache<String, Set<String>> cache;

        private final Supplier<Set<String>> refreshPermissionsRunnable;

        private final Supplier<Set<String>> getPublicPermissionsURISupplier;

        public DefaultAuthUrlProcess() {
            AuthUrlConfig instance = AuthUrlConfig.getInstance();
            AuthUrlConfig.AuthUrlStorage storage = instance.storage();
            final String publicResourceKey = String.join(":", instance.cacheKeyPrefix(), "public");
            cache = CacheManager.cache(instance.localMaximumSize());
            switch (storage) {
                case LOCAL -> {
                    refreshPermissionsRunnable = () -> {
                        List<Permission> permissionInfos = DaoUtils.getPublicAPIPermissionInfos();
                        Set<String> uriSet = StreamUtils.toSet(permissionInfos, Permission::getResource);
                        cache.put(publicResourceKey, uriSet);
                        return uriSet;
                    };

                    getPublicPermissionsURISupplier = () -> {
                        Set<String> uriSet = cache.getIfPresent(publicResourceKey);
                        if (CollectionUtils.isEmpty(uriSet)) {
                            uriSet = refreshPermissionsRunnable.get();
                        }
                        uriSet.addAll(instance.noAuthUrlSet());
                        return uriSet;
                    };
                }
                case REDIS -> {
                    refreshPermissionsRunnable = () -> {
                        List<Permission> permissionInfos = DaoUtils.getPublicAPIPermissionInfos();
                        Set<String> uriSet = StreamUtils.toSet(permissionInfos, Permission::getResource);
                        RedisUtils.set(publicResourceKey, GsonUtils.toJson(uriSet));
                        return uriSet;
                    };
                    getPublicPermissionsURISupplier = () -> {
                        String publicResourceJson = RedisUtils.get(publicResourceKey);
                        Set<String> uriSet;
                        if (StringUtils.isNotBlank(publicResourceJson)) {
                            uriSet = GsonUtils.toObj(publicResourceJson, new TypeToken<Set<String>>() {
                            }.getType());
                        } else {
                            uriSet = refreshPermissionsRunnable.get();
                        }
                        uriSet.addAll(instance.noAuthUrlSet());
                        return uriSet;
                    };
                }
                default -> {
                    refreshPermissionsRunnable = Sets::newHashSet;
                    getPublicPermissionsURISupplier = () -> {
                        List<Permission> permissionInfos = DaoUtils.getPublicAPIPermissionInfos();
                        Set<String> uriSet = StreamUtils.toSet(permissionInfos, Permission::getResource);
                        uriSet.addAll(instance.noAuthUrlSet());
                        return uriSet;
                    };
                }
            }
        }

        @Override
        public void refreshPermissions() {
            refreshPermissionsRunnable.get();
        }

        @Override
        public Set<String> getPublicPermissionsURI() {
            return getPublicPermissionsURISupplier.get();
        }
    }
}
