package io.github.jockerCN.dao;

import io.github.jockerCN.annotation.Description;
import io.github.jockerCN.dao.enums.TokenExpiryStrategy;
import io.github.jockerCN.function.TriConsumer;
import io.github.jockerCN.jpa.BaseJapPojo;
import io.github.jockerCN.jpa.autoRepository.JpaRepositoryUtils;
import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.secret.Cryption;
import io.github.jockerCN.secret.SecureRandomCharacter;
import io.github.jockerCN.token.RefreshTokenRecord;
import io.github.jockerCN.token.TokenProperties;
import io.github.jockerCN.token.TokenRecord;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Consumer;

@Entity
@Table(name = "user_login_info")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginInfo extends BaseJapPojo {

    @Column(name = "user_code", nullable = false, unique = true, length = 24)
    @Description("用户编码")
    private String userCode;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    @Description("用户名，唯一")
    private String username;

    @Column(name = "login_method", nullable = false, length = 32)
    @Description("登录方式")
    private String loginMethod;

    @Column(name = "device_info", nullable = false, length = 128)
    @Description("登录设备（如设备类型、操作系统等）")
    private String deviceInfo;

    @Column(name = "access_token", nullable = false, length = 512)
    @Description("登录token")
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, length = 512)
    @Description("刷新token")
    private String refreshToken;

    @Column(name = "login_time", nullable = false)
    @Description("登录时间")
    private Long loginTime;

    @Column(name = "expiration_time", nullable = false)
    @Description("token过期时间")
    private Long expirationTime;

    @Column(name = "refresh_expiration_time", nullable = false)
    @Description("刷新token过期时间")
    private Long refreshExpirationTime;

    @Column(name = "last_refresh_time", nullable = false)
    @Description("最近一次刷新时间")
    private Long lastRefreshTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_expiry_strategy", nullable = false)
    @Description("token到期策略")
    private TokenExpiryStrategy tokenExpiryStrategy;

    @Column(name = "ip_address", length = 45)
    @Description("登录的IP地址")
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    @Description("用户代理信息（浏览器和操作系统的详细信息）")
    private String userAgent;


    public void clearByDB() {
        UserLoginInfo userLoginInfo = DaoUtils.getUserLoginInfoWithUserCode(userCode);
        if (Objects.nonNull(userLoginInfo)) {
            JpaRepositoryUtils.delete(userLoginInfo);
        }
    }

    public void clearByKey(Consumer<String> clearConsumer) {
        TokenProperties tokenProperties = TokenProperties.getInstance();
        final String cacheTokenKey = tokenProperties.getCacheTokenKey(getAccessToken());
        clearConsumer.accept(cacheTokenKey);
        final String cacheUserInfoKey = tokenProperties.getCacheUserInfoKey(getUserCode());
        clearConsumer.accept(cacheUserInfoKey);
    }

    public void refreshToken() throws Exception {
        TokenProperties tokenProperties = TokenProperties.getInstance();
        LocalDateTime nowTime = LocalDateTime.now();
        ZonedDateTime zonedNowTime = nowTime.atZone(ZoneId.systemDefault());
        long nowEpochMilli = zonedNowTime.toInstant().toEpochMilli();
        final TokenRecord tokenRecord = new TokenRecord(getUsername(), getUserCode(), nowEpochMilli);
        final String newToken = Cryption.getInstance(TokenRecord.class).encryptAsString(GsonUtils.toJson(tokenRecord));
        long lifetimeSeconds = tokenProperties.getLifetimeSeconds();
        long tokenExpiredTime = zonedNowTime.plusSeconds(lifetimeSeconds).toInstant().toEpochMilli();

        RefreshTokenRecord newRefreshToken = new RefreshTokenRecord(userCode, newToken, SecureRandomCharacter.getDefaultRandomCharactersAsString(2, 24));
        long tokenLifetimeSeconds = tokenProperties.getRefreshTokenLifetimeSeconds();
        long refreshExpirationTime = zonedNowTime.plusSeconds(tokenLifetimeSeconds).toInstant().toEpochMilli();

        setRefreshToken(Cryption.getInstance(RefreshTokenRecord.class).encryptAsString(GsonUtils.toJson(newRefreshToken)));
        setRefreshExpirationTime(refreshExpirationTime);
        setIpAddress(RequestContext.getRequestContext().ipAddress());
        setUserAgent(RequestContext.getRequestContext().userAgent());
        setAccessToken(newToken);
        setLastRefreshTime(nowEpochMilli);
        setExpirationTime(tokenExpiredTime);
    }


    public void tokenCacheSet(TriConsumer<String, Object, Duration> cacheSetter) {
        TokenProperties tokenProperties = TokenProperties.getInstance();
        // set access token
        long lifetimeSeconds = tokenProperties.getLifetimeSeconds();
        final String cacheTokenKey = tokenProperties.getCacheTokenKey(getAccessToken());
        cacheSetter.apply(cacheTokenKey, getUserCode(), Duration.ofSeconds(lifetimeSeconds));

        // set refresh token
        long tokenLifetimeSeconds = tokenProperties.getRefreshTokenLifetimeSeconds();
        final String cacheUserInfoKey = tokenProperties.getCacheUserInfoKey(getUserCode());
        cacheSetter.apply(cacheUserInfoKey, this, Duration.ofSeconds(tokenLifetimeSeconds));
    }

    public Consumer<UserLoginInfo> cacheClearAccessToken(Consumer<String> cacheOperator) {
        TokenProperties tokenProperties = TokenProperties.getInstance();
        return (userLoginInfo) -> {
            final String tokenKey = tokenProperties.getCacheTokenKey(userLoginInfo.getAccessToken());
            cacheOperator.accept(tokenKey);
        };
    }

    public String refreshTokenProcess(Consumer<UserLoginInfo> refreshBeforeConsumer, Consumer<UserLoginInfo> refreshAfterConsumer) throws Exception {
        refreshBeforeConsumer.accept(this);
        refreshToken();
        refreshAfterConsumer.accept(this);
        return getAccessToken();
    }

}
