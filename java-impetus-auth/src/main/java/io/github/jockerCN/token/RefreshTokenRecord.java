package io.github.jockerCN.token;


import io.github.jockerCN.gson.GsonUtils;
import io.github.jockerCN.secret.Cryptic;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public record RefreshTokenRecord(
        String userCode,
        String token,
        String salt) {


    public static RefreshTokenRecord parse(String refreshToken) throws Exception {
        String refreshTokenJson = Cryptic.getInstance(RefreshTokenRecord.class).decryptAsString(refreshToken);
        return GsonUtils.toObj(refreshTokenJson, RefreshTokenRecord.class);
    }
}
