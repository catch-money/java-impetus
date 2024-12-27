package io.github.jockerCN.jpa.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class AbstractBaseJapPojo extends JpaPojo {

    @Column(name = "creator")
    private String creator;

    @Column(name = "updater")
    private String updater;

    @Column(name = "deleted ")
    private int deleted;

    public static final String _creator = "creator";
    public static final String _updater = "updater";
    public static final String _deleted = "deleted";

    @Override
    public void customizePrePersist() {
        this.deleted = 1;
    }
}
