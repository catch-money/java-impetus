package io.github.jockerCN.dao.enums;

import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PermissionsAccessLevelEnum implements BaseEnum<PermissionsAccessLevelEnum, String, String> {
    READ("READ", ""), EDIT("EDIT", "");
    private final String value;
    private final String desc;
}