package io.github.jockerCN.permissions;


import com.github.benmanes.caffeine.cache.Cache;
import io.github.jockerCN.cache.CacheManager;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.config.url.AuthUrlConfig;
import io.github.jockerCN.dao.DaoUtils;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.enums.PermissionsAccessLevelEnum;
import io.github.jockerCN.dao.module.PermissionInfo;
import io.github.jockerCN.gson.GsonUtils;
import io.github.jockerCN.redis.RedisUtils;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface GroupPermissionsProcess {


    void refreshGroupPermissions(Set<String> groupIds);

    Set<PermissionInfo> getGroupPermissions(String groupId);

    void clear();

    void remove(String groupId, String permissionId);

    void removeAll(String groupId);

    default Set<PermissionInfo> getGroupPermissions(Set<String> groupIds, Set<PermissionTypeEnum> permissionTypes) {
        Set<PermissionInfo> permissionInfos = new HashSet<>();
        for (String groupId : groupIds) {
            permissionInfos.addAll(getGroupPermissions(groupId));
        }
        PermissionInfo.filterByPermissionType(permissionInfos,permissionTypes);
        return permissionInfos;
    }

    default void set(String groupId, PermissionInfo permissionInfo) {
        Set<PermissionInfo> permissions = getGroupPermissions(groupId);
        permissions.removeIf(s -> s.getPermissionId().equals(permissionInfo.getPermissionId()));
        permissions.add(permissionInfo);
    }

    @Nullable
    default PermissionInfo get(String groupId, String permissionId) {
        Set<PermissionInfo> permissions = getGroupPermissions(groupId);
        if (CollectionUtils.isEmpty(permissions)) {
            return null;
        }
        return permissions.stream().filter(permission -> permission.getPermissionId().equals(permissionId)).findFirst().orElse(null);
    }

    @Nullable
    default PermissionInfo getWithURI(String groupId, String requestURI) {
        Set<PermissionInfo> permissions = getGroupPermissions(groupId);
        if (CollectionUtils.isEmpty(permissions)) {
            return null;
        }
        return permissions.stream().filter(permission -> SpringUtils.antPathMatch(permission.getResource(), requestURI)).findFirst().orElse(null);
    }


    default boolean includeURI(String groupId, String requestURI) {
        return Objects.nonNull(getWithURI(groupId, requestURI));
    }

    @Nullable
    default PermissionsAccessLevelEnum getURIAccessLevel(String groupId, String requestURI) {
        PermissionInfo permissionInfo = getWithURI(groupId, requestURI);
        return Objects.nonNull(permissionInfo) ? permissionInfo.getAccessLevel() : null;
    }

    DefaultGroupPermissionsProcess defaultGroupPermissionsProcess = new DefaultGroupPermissionsProcess();

    static GroupPermissionsProcess getInstance() {
        return SpringProvider.getBeanOrDefault(GroupPermissionsProcess.class, defaultGroupPermissionsProcess);
    }

    class DefaultGroupPermissionsProcess implements GroupPermissionsProcess {

        private final Cache<String, Set<PermissionInfo>> cache;

        private final Consumer<Set<String>> refreshRunnable;

        private final Function<String, Set<PermissionInfo>> getPermissionFunction;

        private final BiConsumer<String, String> removeBiConsumer;

        private final Consumer<String> removeAllConsumer;

        private final Runnable clearRunnable;

        public DefaultGroupPermissionsProcess() {
            AuthUrlConfig instance = AuthUrlConfig.getInstance();
            cache = CacheManager.cache(instance.localMaximumSize());
            AuthUrlConfig.AuthUrlStorage storage = instance.storage();
            final String cacheKeyPrefix = instance.cacheKeyPrefix();

            switch (storage) {
                case LOCAL -> {
                    refreshRunnable = (groupIds) -> {
                        Map<String, Set<PermissionInfo>> permissionAggregate = DaoUtils.getGroupPermissionAggregate(groupIds);
                        for (Map.Entry<String, Set<PermissionInfo>> stringSetEntry : permissionAggregate.entrySet()) {
                            final String cacheKey = String.join(":", cacheKeyPrefix, stringSetEntry.getKey());
                            cache.put(cacheKey, stringSetEntry.getValue());
                        }
                    };
                    getPermissionFunction = cache::getIfPresent;
                    removeBiConsumer = (groupId, permissionId) -> {
                        Set<PermissionInfo> permissions = cache.getIfPresent(groupId);
                        if (Objects.nonNull(permissions)) {
                            permissions.removeIf(s -> Objects.equals(s.getPermissionId(), permissionId));
                        }
                    };
                    removeAllConsumer = (groupId) -> {
                        final String cacheKey = String.join(":", cacheKeyPrefix, groupId);
                        cache.invalidate(cacheKey);
                    };
                    clearRunnable = cache::invalidateAll;
                }
                case REDIS -> {
                    refreshRunnable = (groupIds) -> {
                        Map<String, Set<PermissionInfo>> permissionAggregate = DaoUtils.getGroupPermissionAggregate(groupIds);
                        for (Map.Entry<String, Set<PermissionInfo>> stringSetEntry : permissionAggregate.entrySet()) {
                            final String cacheKey = String.join(":", cacheKeyPrefix, stringSetEntry.getKey());
                            RedisUtils.set(cacheKey, GsonUtils.toJson(stringSetEntry.getValue()));
                        }
                    };

                    getPermissionFunction = (groupId) -> {
                        final String cacheKey = String.join(":", cacheKeyPrefix, groupId);
                        final String permissionJson = RedisUtils.get(cacheKey);
                        if (StringUtils.isBlank(permissionJson)) {
                            Set<PermissionInfo> permissionSetByGroupId = DaoUtils.getGroupPermissionSetByGroupId(groupId);
                            RedisUtils.set(cacheKey, GsonUtils.toJson(permissionSetByGroupId));
                            return permissionSetByGroupId;
                        }
                        return GsonUtils.toSet(permissionJson,PermissionInfo.class);
                    };
                    removeBiConsumer = (groupId, permissionId) -> {
                        final String cacheKey = String.join(":", cacheKeyPrefix, groupId);
                        final String permissionJson = RedisUtils.get(cacheKey);
                        if (StringUtils.isNotBlank(permissionJson)) {
                            return;
                        }
                        Set<PermissionInfo> permissionInfoSet = GsonUtils.toSet(permissionJson,PermissionInfo.class);
                        boolean removed = permissionInfoSet.removeIf(s -> Objects.equals(s.getPermissionId(), permissionId));
                        if (removed) {
                            RedisUtils.set(cacheKey, GsonUtils.toJson(permissionInfoSet));
                        }
                    };
                    removeAllConsumer = (groupId) -> {
                        final String cacheKey = String.join(":", cacheKeyPrefix, groupId);
                        RedisUtils.del(cacheKey);
                    };
                    clearRunnable = () -> {
                        Set<String> cacheKeys = RedisUtils.getKeys(cacheKeyPrefix);
                        RedisUtils.delKeys(cacheKeys);
                    };
                }
                default -> {
                    refreshRunnable = (groupIds) -> {
                    };
                    getPermissionFunction = DaoUtils::getGroupPermissionSetByGroupId;
                    removeBiConsumer = (groupId, permissionId) -> {
                    };
                    removeAllConsumer = (groupId) -> {
                    };
                    clearRunnable = () -> {
                    };
                }
            }
        }

        @Override
        public void refreshGroupPermissions(Set<String> groupIds) {
            refreshRunnable.accept(groupIds);
        }

        @Override
        public Set<PermissionInfo> getGroupPermissions(String groupId) {
            return getPermissionFunction.apply(groupId);
        }


        @Override
        public void clear() {
            clearRunnable.run();
        }

        @Override
        public void remove(String groupId, String permissionId) {
            removeBiConsumer.accept(groupId, permissionId);
        }

        @Override
        public void removeAll(String groupId) {
            removeAllConsumer.accept(groupId);
        }

    }

}
