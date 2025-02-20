package io.github.jockerCN.jpa;


import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.http.request.RequestInfo;
import io.github.jockerCN.jpa.pojo.AbstractBaseJapPojo;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public class BaseJapPojo extends AbstractBaseJapPojo {

    @Override
    public void customizePrePersist() {
        RequestInfo context = RequestContext.getRequestContext();
        if (Objects.nonNull(context)) {
            setCreator(context.userInfo().getUsername());
            setUpdater(context.userInfo().getUsername());
        } else {
            setCreator("SYSTEM");
            setUpdater("SYSTEM");
        }
        setUpdateTime(LocalDateTime.now());
        super.customizePrePersist();
    }

    @Override
    public void customizePreUpdate() {
        RequestInfo context = RequestContext.getRequestContext();
        if (Objects.nonNull(context)) {
            setUpdater(context.userInfo().getUsername());
        }
    }
}
