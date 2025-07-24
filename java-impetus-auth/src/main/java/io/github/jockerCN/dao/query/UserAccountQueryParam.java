package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.annotation.where.IN;
import io.github.jockerCN.customize.annotation.where.Like;
import io.github.jockerCN.dao.UserAccount;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(UserAccount.class)
public class UserAccountQueryParam extends AuthBaseQueryParam {

    @Equals
    private String username;

    @Like("username")
    private String usernameLike;

    @Equals
    private String userCode;

    @IN("userCode")
    private Set<String> userCodes;
}
