package io.github.jockerCN.query.page;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class PageQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;



    @Override
    public void run() {
        PageTest pageTest = new PageTest();

        pageTest.setPageSize(14);
        pageTest.setPage(1);

        List<PayEntity> objects = jpaQueryManager.queryList(pageTest);

        asserts(!objects.isEmpty(),"@Page @PageSize");
    }


    @EqualsAndHashCode(callSuper = true)
    @JpaQuery(PayEntity.class)
    @Data
    public static class PageTest extends BaseQueryParam {

    }

}
