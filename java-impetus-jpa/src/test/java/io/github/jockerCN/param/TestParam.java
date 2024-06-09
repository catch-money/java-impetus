package io.github.jockerCN.param;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.Page;
import io.github.jockerCN.customize.annotation.PageSize;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.annotation.where.GT;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@JpaQuery(PayEntity.class)
@Data
public class TestParam {

    @Page
    private Integer page;

    @PageSize
    private Integer pageSize;

//    @Columns(Object[].class)
    private Set<String> columns;


    @Equals
    private String orderId;

    @Equals
    private Integer paymentType;

    @GT
    @Equals
    private LocalDateTime createTime;
}
