package io.github.jockerCN.dao.enums;


import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TokenExpiryStrategy implements BaseEnum<TokenExpiryStrategy, String, String> {
    AUTO_REFRESH("auto_refresh", "auto_refresh"),
    RE_LOGIN("re_login", "re_login"),
    ;

    private final String value;
    private final String desc;
}