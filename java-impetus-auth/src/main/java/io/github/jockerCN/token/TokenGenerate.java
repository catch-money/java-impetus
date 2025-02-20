package io.github.jockerCN.token;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.dao.UserAccount;
import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.secret.Cryption;
import io.github.jockerCN.secret.SecureRandomCharacter;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface TokenGenerate {


    TokenWrapper generate(Object data) throws Exception;


    boolean support(Class<?> data);


    @Nullable
    static TokenGenerate getInstance(final Class<?> dataClass) {
        Collection<TokenGenerate> generates = SpringProvider.getBeans(TokenGenerate.class);

        for (TokenGenerate generate : generates) {
            if (generate.support(dataClass)) {
                return generate;
            }
        }

        if (defaultGenerate.support(dataClass)) {
            return defaultGenerate;
        }
        return null;
    }

    TokenGenerate defaultGenerate = new TokenGenerate() {
        @Override
        public TokenWrapper generate(Object data) throws Exception {
            TokenInfo tokenInfo = TokenInfo.create((UserAccount) data);
            TokenProperties tokenProperties = TokenProperties.getInstance();

            TokenRecord tokenRecord = new TokenRecord(tokenInfo.getUserCode(), tokenInfo.getUsername(), tokenInfo.getCreateTime());
            tokenInfo.setToken(Cryption.getInstance(data.getClass()).encryptAsString(GsonUtils.toJson(tokenRecord)));
            long lifetimeSeconds = tokenProperties.getLifetimeSeconds();
            tokenInfo.setExpireTime(tokenInfo.getCreateDateTime().plusSeconds(lifetimeSeconds).toInstant().toEpochMilli());

            if (tokenProperties.needRefreshToken()) {
                tokenInfo.setRefreshToken(Cryption.getInstance(data.getClass()).encryptAsString(tokenInfo.getRefreshToken()));
            }

            if (tokenProperties.needRefreshToken()) {
                RefreshTokenRecord refreshTokenRecord = new RefreshTokenRecord(tokenInfo.getUserCode(), tokenInfo.getToken(), SecureRandomCharacter.getDefaultRandomCharactersAsString(2, 24));
                long tokenLifetimeSeconds = tokenProperties.getRefreshTokenLifetimeSeconds();
                tokenInfo.setRefreshToken(Cryption.getInstance(data.getClass()).encryptAsString(GsonUtils.toJson(refreshTokenRecord)));
                tokenInfo.setRefreshExpiresIn(tokenInfo.getCreateDateTime().plusSeconds(tokenLifetimeSeconds).toInstant().toEpochMilli());
            }
            tokenInfo.setExpiryStrategy(tokenProperties.getExpiryStrategy());
            return new TokenWrapper(tokenInfo);
        }

        @Override
        public boolean support(Class<?> data) {
            return UserAccount.class.isAssignableFrom(data);
        }
    };
}
