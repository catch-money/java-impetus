package io.github.jockerCN.api.param;

import io.github.jockerCN.dao.enums.GenderEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class UserProfileUpdate {

    private String userCode;

    private String fullName;

    private GenderEnum gender;

    private LocalDateTime birthdate;

    private String address;
}
