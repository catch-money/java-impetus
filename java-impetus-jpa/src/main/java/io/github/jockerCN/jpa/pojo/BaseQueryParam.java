package io.github.jockerCN.jpa.pojo;

import io.github.jockerCN.customize.OderByCondition;
import io.github.jockerCN.customize.SelectColumn;
import io.github.jockerCN.customize.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public abstract class BaseQueryParam {

    @PageSize
    private Integer pageSize;

    @Page
    private Integer page;

    @Limit
    private Integer limitCount;


    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private int deleted = 1;

    @Columns
    private Set<SelectColumn> queryColumns;

    @OrderBy(OderByCondition.DESC)
    private Set<String> descColumns;

    @OrderBy
    private Set<String> ascColumns;
}
