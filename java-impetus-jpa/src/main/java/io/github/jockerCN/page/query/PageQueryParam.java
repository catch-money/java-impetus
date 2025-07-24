package io.github.jockerCN.page.query;

import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class PageQueryParam extends BaseQueryParam {

    private String module;

    @Equals
    private Long id;
}
