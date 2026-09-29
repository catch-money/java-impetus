package io.github.jockerCN.query.orderBy;

import io.github.jockerCN.jpa.query.model.OderByCondition;
import io.github.jockerCN.jpa.query.model.NullOrder;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.OrderBy;
import io.github.jockerCN.jpa.annotation.where.IN;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class OrderByQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;



    @Override
    public void run() {
        OrderByParamDesc orderByParam = new OrderByParamDesc();
        orderByParam.setOrderBy(Set.of("createTime"));


        List<PayEntity> objects = jpaQueryManager.queryList(orderByParam);

        boolean isSortedDescending = IntStream.range(1, objects.size())
                .allMatch(i -> objects.get(i - 1).getCreateTime().isAfter(objects.get(i).getCreateTime()) || objects.get(i - 1).getCreateTime().isEqual(objects.get(i).getCreateTime()));

        asserts(isSortedDescending,"@OrderBy Desc");

        OrderByParamAsc orderByParamAsc = new OrderByParamAsc();
        orderByParamAsc.setOrderBy(Set.of("createTime"));


        List<PayEntity> asc = jpaQueryManager.queryList(orderByParamAsc);

        boolean isSortAsc = IntStream.range(1, asc.size())
                .allMatch(i -> asc.get(i - 1).getCreateTime().isBefore(asc.get(i).getCreateTime()) || asc.get(i - 1).getCreateTime().isEqual(asc.get(i).getCreateTime()));

        asserts(isSortAsc,"@OrderBy ASC");

        assertNullOrdering();
    }

    private void assertNullOrdering() {
        List<OptionalNote> originalNotes = jdbcTemplate.query(
                "SELECT id, optional_note FROM jpa.pay WHERE id IN (1, 2, 3)",
                (rs, rowNum) -> new OptionalNote(rs.getLong(1), rs.getString(2)));
        try {
            jdbcTemplate.update("UPDATE jpa.pay SET optional_note = NULL WHERE id = 1");
            jdbcTemplate.update("UPDATE jpa.pay SET optional_note = 'same' WHERE id IN (2, 3)");

            OrderByNullFirstParam first = new OrderByNullFirstParam();
            first.setIds(Set.of(1L, 2L, 3L));
            first.setOrderBy(List.of("optionalNote", "id"));
            List<Long> firstIds = jpaQueryManager.<PayEntity>queryList(first).stream().map(PayEntity::getId).toList();
            asserts(firstIds.equals(List.of(1L, 3L, 2L)), "@OrderBy DESC NULLS FIRST and field order");

            OrderByNullLastParam last = new OrderByNullLastParam();
            last.setIds(Set.of(1L, 2L, 3L));
            last.setOrderBy(List.of("optionalNote", "id"));
            List<Long> lastIds = jpaQueryManager.<PayEntity>queryList(last).stream().map(PayEntity::getId).toList();
            asserts(lastIds.equals(List.of(2L, 3L, 1L)), "@OrderBy ASC NULLS LAST and field order");
        } finally {
            originalNotes.forEach(note -> jdbcTemplate.update(
                    "UPDATE jpa.pay SET optional_note = ? WHERE id = ?", note.value(), note.id()));
        }
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class OrderByParamDesc {

        @OrderBy(OderByCondition.DESC)
        private Set<String> orderBy;
    }



    @JpaQuery(PayEntity.class)
    @Data
    public static class OrderByParamAsc {

        @OrderBy
        private Set<String> orderBy;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class OrderByNullFirstParam {
        @IN("id")
        private Set<Long> ids;

        @OrderBy(value = OderByCondition.DESC, nulls = NullOrder.FIRST)
        private List<String> orderBy;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class OrderByNullLastParam {
        @IN("id")
        private Set<Long> ids;

        @OrderBy(nulls = NullOrder.LAST)
        private List<String> orderBy;
    }

    private record OptionalNote(long id, String value) {
    }
}
