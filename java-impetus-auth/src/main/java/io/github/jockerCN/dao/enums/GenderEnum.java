package io.github.jockerCN.dao.enums;

import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@AllArgsConstructor
@Getter
public enum GenderEnum implements BaseEnum<GenderEnum, String, String> {

    MALE("MALE", "男性"),
    FEMALE("FEMALE", "女性"),
    OTHER("OTHER", "其他"),
    ;


    private final String value;
    private final String desc;
}
