package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.dao.UserLoginInfo;
import io.github.jockerCN.page.query.PageQueryParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(UserLoginInfo.class)
public class UserLoginInfoQueryParam extends PageQueryParam {

    @Equals
    private String accessToken;

    @Equals
    private String refreshToken;

    @Equals
    private String userCode;
}
