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
import java.util.Objects;

@TestConfiguration
public class ProjectionFindTypeQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager queryManager;

    @Override
    public void run() {
        ColumnsParam queryParam = new ColumnsParam();
        queryParam.id = 1;
        queryParam.columns = List.of(SelectColumn.of("id"), SelectColumn.of("customerPhone"));

        Object[] array = queryManager.query(queryParam, Object[].class);
        asserts(array != null && array.length == 2 && Long.valueOf(1L).equals(array[0])
                        && "13725090127".equals(array[1]),
                "@Columns honors explicit Object[] findType");
        Object untypedRow = queryManager.query(queryParam, Object.class);
        asserts(untypedRow instanceof Object[] values && values.length == 2
                        && Long.valueOf(1L).equals(values[0]) && "13725090127".equals(values[1]),
                "Object findType preserves a multi-column array result");

        Tuple tuple = queryManager.query(queryParam, Tuple.class);
        asserts(tuple != null && Long.valueOf(1L).equals(tuple.get("id", Long.class)),
                "the same @Columns parameter supports Tuple findType");
        List<Tuple> tuples = queryManager.queryList(queryParam, Tuple.class);
        asserts(tuples.size() == 1 && Long.valueOf(1L).equals(tuples.getFirst().get("id", Long.class)),
                "list query supports Tuple findType");

        ConstructorView view = queryManager.query(queryParam, ConstructorView.class);
        asserts(view != null && Long.valueOf(1L).equals(view.id)
                        && "13725090127".equals(view.phone),
                "the same @Columns parameter supports constructor findType");
        List<ConstructorView> views = queryManager.queryList(queryParam, ConstructorView.class);
        asserts(views.size() == 1 && Long.valueOf(1L).equals(views.getFirst().id),
                "list query honors explicit constructor findType");

        queryParam.columns = List.of(SelectColumn.of("id"));
        Object[] partial = queryManager.query(queryParam, Object[].class);
        asserts(partial != null && partial.length == 1 && Long.valueOf(1L).equals(partial[0]),
                "explicit findType follows dynamic columns");
        asserts(Long.valueOf(1L).equals(queryManager.query(queryParam, Object.class)),
                "Object findType preserves a single-column scalar result on the same parameter");

        queryParam.columns = List.of(SelectColumn.of("id"), SelectColumn.of("customerPhone"));
        PayEntity implicitEntity = queryManager.query(queryParam);
        asserts(implicitEntity != null && Long.valueOf(1L).equals(implicitEntity.getId())
                        && "13725090127".equals(implicitEntity.getCustomerPhone())
                        && implicitEntity.getPayId() == null,
                "without explicit findType, the query returns a partially populated entity");

        queryParam.columns = List.of(SelectColumn.of("customerName"));
        NameView nameView = queryManager.query(queryParam, NameView.class);
        asserts(nameView != null && nameView.name != null,
                "changing selected columns allows a matching DTO findType on the same parameter");

        queryParam.columns = List.of(SelectColumn.of("customerName"), SelectColumn.of("customerPhone"));
        String[] textColumns = queryManager.query(queryParam, String[].class);
        asserts(textColumns != null && textColumns.length == 2
                        && Objects.equals(Objects.requireNonNull(nameView).name, textColumns[0]) && "13725090127".equals(textColumns[1]),
                "typed array findType preserves the provider's element order and array type");
    }

    @JpaQuery(PayEntity.class)
    public static class ColumnsParam {
        @Equals
        private Integer id;

        @Columns
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
