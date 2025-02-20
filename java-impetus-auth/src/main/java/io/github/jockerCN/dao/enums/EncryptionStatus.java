package io.github.jockerCN.dao.enums;

import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum EncryptionStatus implements BaseEnum<EncryptionStatus, String, String> {
    ACTIVE("ACTIVE", "可用"),
    EXPIRED("EXPIRED", "过期"),
    REVOKED("REVOKED", "撤销");
    private final String value;
    private final String desc;
}