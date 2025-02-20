package io.github.jockerCN.dao;

import io.github.jockerCN.dao.enums.GenderEnum;
import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "user_profile")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile extends BaseJapPojo {

    @Column(name = "user_code", length = 24)
    private String userCode;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private GenderEnum gender;

    @Column(name = "birthdate")
    private LocalDate birthdate;

    @Column(name = "locale", length = 10, nullable = false, columnDefinition = "VARCHAR(10) DEFAULT 'zh_CN'")
    private String locale;

    @Column(name = "timezone", length = 50, nullable = false, columnDefinition = "VARCHAR(50) DEFAULT 'UTC+8'")
    private String timezone;

    @Column(name = "profile_url", columnDefinition = "TEXT")
    private String profileUrl;

    @Column(name = "id_card_number", length = 24, nullable = false)
    private String idCardNumber;

    @Column(name = "id_card_name", length = 32, nullable = false)
    private String idCardName;

    @Column(name = "id_card_issued_date")
    private LocalDate idCardIssuedDate;

    @Column(name = "id_card_expiration")
    private LocalDate idCardExpiration;

    @Column(name = "address", length = 255, nullable = false)
    private String address;
}