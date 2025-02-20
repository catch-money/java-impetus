package io.github.jockerCN.permissions;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.jockerCN.cache.CacheManager;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.config.AuthUserInfoProperties;
import io.github.jockerCN.dao.DaoUtils;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.module.UserPermissionAggregate;
import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.redis.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface UserPermissionsProcess {

    void aggregate(String userCode);

    UserPermissionAggregate getUserPermissionAggregate(String userCode);

    void remove(String userCode);

    default UserPermissionAggregate getUserPermissions(String userCode, Set<PermissionTypeEnum> permissionTypeEnum) {
        UserPermissionAggregate userPermissionAggregate = getUserPermissionAggregate(userCode);
        if (Objects.nonNull(permissionTypeEnum)) {
            userPermissionAggregate.getPermissionInfos().removeIf(permissionInfo -> !permissionTypeEnum.contains(permissionInfo.getPermissionType()));
        }
        return userPermissionAggregate;
    }

    static UserPermissionsProcess getInstance() {
        return SpringProvider.getBeanOrDefault(UserPermissionsProcess.class, defaultUserPermissionsProcess);
    }


    UserPermissionsProcess defaultUserPermissionsProcess = new DefaultUserPermissionsProcess();

    @Slf4j
    class DefaultUserPermissionsProcess implements UserPermissionsProcess {

        private final Cache<String, UserPermissionAggregate> cache;

        private final Consumer<String> aggregateConsumer;
        private final Consumer<String> removeConsumer;
        private final Function<String, UserPermissionAggregate> getAggregateConsumer;

        public DefaultUserPermissionsProcess() {
            final AuthUserInfoProperties instance = AuthUserInfoProperties.getInstance();
            final AuthUserInfoProperties.UserPermissionStorage userPermissionStorage = instance.getUserPermission().getStorage();
            log.info("### DefaultUserPermissionsProcess#aggregate use strategy[{}]", userPermissionStorage);
            cache = CacheManager.cache(instance.getUserPermission().getLocalMaximumSize());
            switch (instance.getUserPermission().getStorage()) {
                case LOCAL -> {
                    aggregateConsumer = userCode -> {
                        UserPermissionAggregate userPermissionAggregate = DaoUtils.getUserPermissionAggregate(userCode);
                        final String cacheKey = String.join(":", instance.getUserPermission().getCacheKeyPrefix(), userCode);
                        cache.put(cacheKey, userPermissionAggregate);
                    };
                    removeConsumer = userCode -> {
                        final String cacheKey = String.join(":", instance.getUserPermission().getCacheKeyPrefix(), userCode);
                        cache.invalidate(cacheKey);
                    };
                    getAggregateConsumer = (userCode) -> {
                        final String cacheKey = String.join(":", instance.getUserPermission().getCacheKeyPrefix(), userCode);
                        UserPermissionAggregate userPermissionAggregate = cache.getIfPresent(cacheKey);
                        if (Objects.isNull(userPermissionAggregate)) {
                            UserPermissionAggregate permissionAggregate = DaoUtils.getUserPermissionAggregate(userCode);
                            aggregateConsumer.accept(userCode);
                            return permissionAggregate;
                        }
                        return userPermissionAggregate;
                    };
                }
                case REDIS -> {
                    aggregateConsumer = userCode -> {
                        UserPermissionAggregate userPermissionAggregate = DaoUtils.getUserPermissionAggregate(userCode);
                        final String cacheKey = String.join(":", instance.getUserPermission().getCacheKeyPrefix(), userCode);
                        RedisUtils.set(cacheKey, GsonUtils.toJson(userPermissionAggregate));
                    };
                    removeConsumer = userCode -> {
                        final String cacheKey = String.join(":", instance.getUserPermission().getCacheKeyPrefix(), userCode);
                        RedisUtils.del(cacheKey);
                    };
                    getAggregateConsumer = (userCode) -> {
                        final String cacheKey = String.join(":", instance.getUserPermission().getCacheKeyPrefix(), userCode);
                        final String aggregateJson = RedisUtils.get(cacheKey);
                        if (StringUtils.isBlank(aggregateJson)) {
                            UserPermissionAggregate permissionAggregate = DaoUtils.getUserPermissionAggregate(userCode);
                            aggregateConsumer.accept(userCode);
                            return permissionAggregate;
                        }
                        return GsonUtils.toObj(aggregateJson, UserPermissionAggregate.class);
                    };
                }
                default -> {
                    aggregateConsumer = userCode -> {
                    };
                    removeConsumer = userCode -> {
                    };
                    getAggregateConsumer = DaoUtils::getUserPermissionAggregate;
                }
            }
        }

        @Override
        public void aggregate(String userCode) {
            log.info("### DefaultUserPermissionsProcess#aggregate process userCode [{}]", userCode);
            aggregateConsumer.accept(userCode);
        }

        @Nullable
        @Override
        public UserPermissionAggregate getUserPermissionAggregate(String userCode) {
            return getAggregateConsumer.apply(userCode);
        }

        @Override
        public void remove(String userCode) {
            removeConsumer.accept(userCode);
        }
    }


}
