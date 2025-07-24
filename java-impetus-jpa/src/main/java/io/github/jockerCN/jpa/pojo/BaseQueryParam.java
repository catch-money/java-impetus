package io.github.jockerCN.jpa.pojo;

import io.github.jockerCN.customize.OderByCondition;
import io.github.jockerCN.customize.SelectColumn;
import io.github.jockerCN.customize.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@NoArgsConstructor
public abstract class BaseQueryParam {

    @PageSize
    private Integer pageSize;

    @Page
    private Integer page;

    @Limit
    private Integer limitCount;

    @Columns
    private Set<SelectColumn> queryColumns;

    @OrderBy(OderByCondition.DESC)
    private Set<String> descColumns;

    @OrderBy
    private Set<String> ascColumns;
}
