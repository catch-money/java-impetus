package io.github.jockerCN.query.likeAndNotLike;

import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.where.Like;
import io.github.jockerCN.jpa.annotation.where.ILike;
import io.github.jockerCN.jpa.annotation.where.NotLike;
import io.github.jockerCN.jpa.annotation.where.NotILike;
import io.github.jockerCN.jpa.annotation.where.IN;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.Set;

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

        CaseInsensitiveParam caseInsensitive = new CaseInsensitiveParam();
        caseInsensitive.setIds(Set.of(1L, 2L, 3L));
        caseInsensitive.setTradeState("%sUcCeSs%");
        List<PayEntity> matching = jpaQueryManager.queryList(caseInsensitive, PayEntity.class);
        asserts(matching.size() == 1 && matching.getFirst().getId() == 1L, "@ILike");

        caseInsensitive.setTradeState(null);
        caseInsensitive.setNotILike("%sUcCeSs%");
        List<PayEntity> notMatching = jpaQueryManager.queryList(caseInsensitive, PayEntity.class);
        asserts(notMatching.size() == 2 && notMatching.stream().noneMatch(entity -> entity.getId() == 1L),
                "@NotILike");

        caseInsensitive.setNotILike(null);
        asserts(jpaQueryManager.queryList(caseInsensitive, PayEntity.class).size() == 3,
                "null ILIKE parameters are omitted");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class NotLikeParam {


        @NotLike("customerName")
        private String notLike;

        @Like
        private String customerName;

    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class CaseInsensitiveParam {
        @IN("id")
        private Set<Long> ids;

        @ILike
        private String tradeState;

        @NotILike("tradeState")
        private String notILike;
    }
}
