package io.github.jockerCN.api.param;

import io.github.jockerCN.dao.enums.GenderEnum;
import io.github.jockerCN.dao.enums.UserStatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class UserAccountAddOrUpdate {

    private Long id;
    @NotBlank(message = "用户名为空")
    private String username;
    @NotBlank(message = "用户邮箱为空")
    private String email;
    @NotBlank(message = "手机号为空")
    private String phone;

    @NotNull(message = "用户状态为空")
    private UserStatusEnum status;
    @NotBlank(message = "用户类型为空")
    private String userType;
    @NotBlank(message = "用户姓名为空")
    private String fullName;
    @NotNull(message = "用户性别为空")
    private GenderEnum gender;
    @NotNull(message = "用户生日为空")
    private LocalDate birthdate;
    @NotNull(message = "用户语言地区为空")
    private String locale;
    @NotNull(message = "用户时区为空")
    private String timezone;
    private String idCardNumber;
    private String idCardName;
    private LocalDate idCardIssuedDate;
    private LocalDate idCardExpiration;
    private String address;
}
