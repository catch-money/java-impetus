package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.dao.UserLoginInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(UserLoginInfo.class)
public class UserLoginInfoQueryParam extends AuthBaseQueryParam {

    @Equals
    private String accessToken;

    @Equals
    private String refreshToken;

    @Equals
    private String userCode;
}
