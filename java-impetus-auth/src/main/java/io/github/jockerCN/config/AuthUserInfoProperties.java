package io.github.jockerCN.config;

import io.github.jockerCN.common.SpringProvider;
import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@ConfigurationProperties("jocker-cn.auth.user-info.config")
public class AuthUserInfoProperties {


    private UserPermission userPermission = UserPermission.builder()
            .storage(UserPermissionStorage.NO_STORAGE)
            .build();


    private String tempUserName = "anyone";


    public static AuthUserInfoProperties getInstance() {
        return SpringProvider.getBean(AuthUserInfoProperties.class);
    }

    @Data
    @Builder
    public static class UserPermission {

        @Builder.Default
        private long localMaximumSize = 1000;

        @Builder.Default
        private UserPermissionStorage storage = UserPermissionStorage.NO_STORAGE;

        @Builder.Default
        private String cacheKeyPrefix = "auth:user_info:permission";
    }

    public enum UserPermissionStorage {
        LOCAL,
        REDIS,
        NO_STORAGE
    }
}
