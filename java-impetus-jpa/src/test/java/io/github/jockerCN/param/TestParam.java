package io.github.jockerCN.param;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.Page;
import io.github.jockerCN.customize.annotation.where.Equals;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@JpaQuery(TestParam.class)
@Data
public class TestParam {


    @Page
    private Integer page;

    @Equals("orderId")
    private String orderId;

    @Equals("email")
    private String email;

    @Equals("remark")
    private String params;
}
