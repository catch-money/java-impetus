package io.github.jockerCN.token;

import io.github.jockerCN.dao.UserAccount;
import io.github.jockerCN.dao.enums.TokenExpiryStrategy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {

    private String username;

    private String userCode;

    private String token;

    private String refreshToken;

    private long refreshExpiresIn;

    private TokenExpiryStrategy expiryStrategy;

    private ZonedDateTime createDateTime;

    private long createTime;

    private long expireTime;

    private long refreshTime;

    public static TokenInfo create(final UserAccount userAccount) {
        LocalDateTime nowTime = LocalDateTime.now();
        ZonedDateTime zonedNowTime = nowTime.atZone(ZoneId.systemDefault());
        long nowEpochMilli = zonedNowTime.toInstant().toEpochMilli();
        return TokenInfo.builder()
                .username(userAccount.getUsername())
                .userCode(userAccount.getUserCode())
                .createDateTime(zonedNowTime)
                .createTime(nowEpochMilli)
                .refreshTime(nowEpochMilli)
                .expireTime(nowEpochMilli)
                .build();
    }
}
