package io.github.jockerCN.jpa.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public abstract class JpaPojoDTO {


    private long id;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String creator;

    private String updater;

    private int deleted;
}
