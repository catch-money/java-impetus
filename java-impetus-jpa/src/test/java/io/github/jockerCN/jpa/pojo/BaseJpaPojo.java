package io.github.jockerCN.jpa.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */


@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public class BaseJpaPojo extends JpaPojo {


    @Column(name = "creator")
    @Transient
    private String creator;

    @Column(name = "updater")
    @Transient
    private String updater;

    @Column(name = "deleted", insertable = false, updatable = true)
    private int deleted;

    public static final String _id = "id";
    public static final String _createTime = "createTime";
    public static final String _updateTime = "updateTime";
    public static final String _creator = "creator";
    public static final String _updater = "updater";
    public static final String _deleted = "deleted";

    @Override
    public void customizePreUpdate() {

    }

    @Override
    public void customizePrePersist() {

    }


}
