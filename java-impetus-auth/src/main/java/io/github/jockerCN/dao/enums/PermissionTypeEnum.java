package io.github.jockerCN.dao.enums;

import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PermissionTypeEnum implements BaseEnum<PermissionTypeEnum, String, String> {
    PAGE("PAGE", "页面"), BUTTON("BUTTON", "按钮"), API("API", "接口");

    private final String value;
    private final String desc;
}