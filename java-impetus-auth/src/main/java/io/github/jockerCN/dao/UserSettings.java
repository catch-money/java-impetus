package io.github.jockerCN.dao;

import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings extends BaseJapPojo {

    @Column(name = "user_code", length = 24)
    private String userCode;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "sso_enabled", length = 64)
    private boolean ssoEnabled;

    @Column(name = "2fa_enabled", nullable = false)
    private boolean twoFaEnabled;

    @Column(name = "2fa_method", length = 24)
    private String twoFaMethod;


}
