package io.github.jockerCN.query.likeAndNotLike;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Like;
import io.github.jockerCN.customize.annotation.where.NotLike;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class LikeAndNotLikeQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        NotLikeParam paramNotLike = new NotLikeParam();
        paramNotLike.setNotLike("%李威宏%");
        List<PayEntity> queryList = jpaQueryManager.queryList(paramNotLike, PayEntity.class);
        long count = queryList.stream().map(PayEntity::getCustomerName).filter(s -> s.equals("李威宏")).count();

        asserts(count == 0, "@NotLike");


        NotLikeParam like = new NotLikeParam();
        like.setCustomerName("%李威宏%");

        List<PayEntity> queryList2 = jpaQueryManager.queryList(like, PayEntity.class);
        long count2 = queryList2.stream().map(PayEntity::getCustomerName).filter(s -> s.equals("李威宏")).count();

        asserts(count2 == 1, "@Like");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class NotLikeParam {


        @NotLike("customerName")
        private String notLike;

        @Like
        private String customerName;

    }
}
