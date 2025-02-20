package io.github.jockerCN.dao;

import io.github.jockerCN.dao.enums.UserStatusEnum;
import io.github.jockerCN.dao.query.UserAccountQueryParam;
import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Entity
@Table(name = "user_account")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount extends BaseJapPojo {

    @Column(name = "user_code", length = 24)
    private String userCode;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 24)
    private String phone;

    @Column(name = "id_card_number", length = 24)
    private String idCardNumber;

    @Column(name = "algorithm_code", length = 24)
    private String algorithmCode;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatusEnum status;

    @Column(name = "last_login", nullable = false)
    private Long lastLogin;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until", nullable = false)
    private Long lockedUntil;

    @Column(name = "user_type", length = 24)
    private String userType;


    public static UserAccount getByUsername(String username) {
        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setUsername(username);
        return DaoUtils.getUserAccount(queryParam);
    }
}
