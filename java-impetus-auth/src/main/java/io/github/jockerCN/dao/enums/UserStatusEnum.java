package io.github.jockerCN.dao.enums;

import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@AllArgsConstructor
@Getter
public enum UserStatusEnum implements BaseEnum<UserStatusEnum, String, String> {

    PENDING("PENDING", "待激活"),
    ACTIVE("ACTIVE", "可用"),
    INACTIVE("INACTIVE", "尚未激活"),
    LOCKED("LOCKED", "被锁定"),
    SUSPENDED("SUSPENDED", "暂停使用"),
    ;


    private final String value;
    private final String desc;
}
