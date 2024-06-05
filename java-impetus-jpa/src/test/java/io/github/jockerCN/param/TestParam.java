package io.github.jockerCN.param;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@JpaQuery(TestParam.class)
public class TestParam {


    @Equals("orderId")
    private String orderId;

    @Equals("email")
    private String email;

    @Equals("remark")
    private String params;
}
