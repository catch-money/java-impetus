package io.github.jockerCN.token;


import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.secret.Cryption;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public record RefreshTokenRecord(
        String userCode,
        String token,
        String salt) {


    public static RefreshTokenRecord parse(String refreshToken) throws Exception {
        String refreshTokenJson = Cryption.getInstance(RefreshTokenRecord.class).decryptAsString(refreshToken);
        return GsonUtils.toObj(refreshTokenJson, RefreshTokenRecord.class);
    }
}
