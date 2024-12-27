package io.github.jockerCN.jpa.pojo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */


@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public abstract class JpaPojo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "create_time", insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createTime;

    @Column(name = "update_time", insertable = false, updatable = true, columnDefinition = "TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)")
    private LocalDateTime updateTime;

    public static final String _id = "id";
    public static final String _createTime = "createTime";
    public static final String _updateTime = "updateTime";

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now(); // Set the current time before persisting
        customizePrePersist();
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now(); // Set the current time before persisting
        customizePreUpdate();
    }


    public abstract void customizePreUpdate();


    public abstract void customizePrePersist();

}
