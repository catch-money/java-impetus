package io.github.jockerCN.dao;

import io.github.jockerCN.dao.enums.EncryptionStatus;
import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "encryption_data")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionData extends BaseJapPojo {


    @Column(name = "algorithm_desc", length = 32, nullable = false)
    private String algorithmDesc;

    @Column(name = "algorithm_code", length = 24, unique = true, nullable = false)
    private String algorithmCode;

    @Column(name = "version", length = 32, nullable = false)
    private String version;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String algorithmKey;

    @Column(name = "salt", length = 255)
    private String salt;

    @Column(name = "expire_at", nullable = false)
    private long expireAt;

    @Column(name = "encryption_metadata", columnDefinition = "TEXT")
    private String encryptionMetadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EncryptionStatus status;

}