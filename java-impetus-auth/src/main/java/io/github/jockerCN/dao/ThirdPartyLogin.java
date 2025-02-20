package io.github.jockerCN.dao;


import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "third_party_login")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyLogin extends BaseJapPojo {


    @Column(name = "user_code", length = 24, nullable = false)
    private String userCode;

    @Column(name = "provider", length = 64, nullable = false)
    private String provider;

    @Column(name = "provider_id", length = 128, nullable = false)
    private String providerId;

    @Column(name = "access_token", length = 512, nullable = false)
    private String accessToken;

    @Column(name = "refresh_token", length = 512, nullable = false)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;

    @Column(name = "linked_at", nullable = false)
    private long linkedAt;

    @Column(name = "third_username", length = 128, nullable = false)
    private String thirdUsername;

    @Column(name = "third_avatar_url", length = 512, nullable = false)
    private String thirdAvatarUrl;

    @Column(name = "specific_info", columnDefinition = "TEXT")
    private String specificInfo;
}