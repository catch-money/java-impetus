package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.page.query.PageQueryParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AuthBaseQueryParam extends PageQueryParam {

    @Equals
    private Integer deleted = 1;
}
