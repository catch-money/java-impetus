package io.github.jockerCN.query.columns;

import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.query.QueryAnnotationTest;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@TestConfiguration
public class ProjectionFindTypeQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager queryManager;

    @Override
    public void run() {
        TupleColumnsParam tupleParam = new TupleColumnsParam();
        tupleParam.id = 1;
        tupleParam.columns = List.of(SelectColumn.of("id"), SelectColumn.of("customerPhone"));

        Object[] array = queryManager.query(tupleParam, Object[].class);
        asserts(array != null && array.length == 2 && Long.valueOf(1L).equals(array[0])
                        && "13725090127".equals(array[1]),
                "default @Columns Tuple strategy honors explicit Object[] findType");

        ConstructorView view = queryManager.query(tupleParam, ConstructorView.class);
        asserts(view != null && Long.valueOf(1L).equals(view.id)
                        && "13725090127".equals(view.phone),
                "default @Columns Tuple strategy honors explicit constructor findType");
        List<ConstructorView> views = queryManager.queryList(tupleParam, ConstructorView.class);
        asserts(views.size() == 1 && Long.valueOf(1L).equals(views.getFirst().id),
                "list query honors explicit constructor findType");

        tupleParam.columns = List.of(SelectColumn.of("id"));
        Object[] partial = queryManager.query(tupleParam, Object[].class);
        asserts(partial != null && partial.length == 1 && Long.valueOf(1L).equals(partial[0]),
                "explicit findType follows dynamic columns");

        tupleParam.columns = List.of(SelectColumn.of("id"), SelectColumn.of("customerPhone"));
        PayEntity implicitTupleResult = queryManager.query(tupleParam);
        asserts(implicitTupleResult != null && Long.valueOf(1L).equals(implicitTupleResult.getId())
                        && "13725090127".equals(implicitTupleResult.getCustomerPhone())
                        && implicitTupleResult.getPayId() == null,
                "without explicit findType, the query returns a partially populated entity");

        ArrayColumnsParam arrayParam = new ArrayColumnsParam();
        arrayParam.id = 1;
        arrayParam.columns = List.of(SelectColumn.of("id"), SelectColumn.of("customerPhone"));
        Object[] explicitArray = queryManager.query(arrayParam, Object[].class);
        asserts(explicitArray != null && explicitArray.length == 2
                        && Long.valueOf(1L).equals(explicitArray[0]),
                "@Columns(Object[].class) result");

        Tuple tupleOverride = queryManager.query(arrayParam, Tuple.class);
        asserts(tupleOverride != null && Long.valueOf(1L).equals(tupleOverride.get("id", Long.class)),
                "explicit Tuple findType overrides @Columns(Object[].class)");
        List<Tuple> tupleList = queryManager.queryList(arrayParam, Tuple.class);
        asserts(tupleList.size() == 1 && Long.valueOf(1L).equals(tupleList.getFirst().get("id", Long.class)),
                "list query overrides @Columns(Object[].class) with Tuple findType");

        assertThrows(RuntimeException.class, () -> queryManager.query(arrayParam),
                "Without findType, the current query entry uses the entity type, not @Columns.value");

        EntityConstructorParam constructorParam = new EntityConstructorParam();
        constructorParam.id = 1;
        constructorParam.columns = List.of(SelectColumn.of("customerName"));
        PayEntity constructed = queryManager.query(constructorParam, PayEntity.class);
        asserts(constructed != null && constructed.getCustomerName() != null,
                "@Columns entity constructor result");

        assertThrows(RuntimeException.class, () -> queryManager.query(constructorParam, NameView.class),
                "Explicit DTO findType currently conflicts with the precompiled entity constructor selection");
    }

    @JpaQuery(PayEntity.class)
    public static class TupleColumnsParam {
        @Equals
        private Integer id;

        @Columns
        private List<SelectColumn> columns;
    }

    @JpaQuery(PayEntity.class)
    public static class ArrayColumnsParam {
        @Equals
        private Integer id;

        @Columns(Object[].class)
        private List<SelectColumn> columns;
    }

    @JpaQuery(PayEntity.class)
    public static class EntityConstructorParam {
        @Equals
        private Integer id;

        @Columns(PayEntity.class)
        private List<SelectColumn> columns;
    }

    public static class ConstructorView {
        private final Long id;
        private final String phone;

        public ConstructorView(Long id, String phone) {
            this.id = id;
            this.phone = phone;
        }
    }

    public static class NameView {
        private final String name;

        public NameView(String name) {
            this.name = name;
        }
    }
}
