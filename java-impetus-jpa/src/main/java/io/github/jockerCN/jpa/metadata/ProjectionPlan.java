package io.github.jockerCN.jpa.metadata;

import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.function.Function;

import static io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess.validateFieldType;

/** A projection operation compiled once for an annotated query-parameter field. */
final class ProjectionPlan {

    private final Function<Object, Object> valueReader;

    private ProjectionPlan(Function<Object, Object> valueReader) {
        this.valueReader = valueReader;
    }

    static ProjectionPlan compile(Field field, Function<Object, Object> valueReader) {
        validateFieldType(field, "@Columns", Collection.class, SelectColumn.class);
        return new ProjectionPlan(valueReader);
    }

    void apply(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Root<?> root, Object queryParams) {
        Collection<SelectColumn> columns = TypeConvert.cast(valueReader.apply(queryParams));
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        Selection<?>[] selections = columns.stream()
                .filter(column -> column.includes(queryParams))
                .map(column -> column.selection(criteriaBuilder, root, queryParams))
                .toArray(Selection[]::new);
        if (selections.length == 0) {
            throw new IllegalArgumentException("@Columns has no selected fields after conditions");
        }
        criteriaQuery.multiselect(selections);
    }
}
