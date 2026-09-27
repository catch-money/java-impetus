package io.github.jockerCN.jpa.metadata;

import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CompoundSelection;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.function.Function;

import static io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess.validateFieldType;

/** A projection strategy compiled once for an annotated query-parameter field. */
final class ProjectionPlan {

    @FunctionalInterface
    private interface ProjectionOperation {
        void apply(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Selection<?>[] selections);
    }

    private final Function<Object, Object> valueReader;
    private final ProjectionOperation operation;

    private ProjectionPlan(Function<Object, Object> valueReader, ProjectionOperation operation) {
        this.valueReader = valueReader;
        this.operation = operation;
    }

    static ProjectionPlan compile(Field field, Columns columns, Class<?> entityType,
                                  Function<Object, Object> valueReader) {
        validateFieldType(field, "@Columns", Collection.class, SelectColumn.class);
        return new ProjectionPlan(valueReader, projectionOperation(columns.value(), entityType));
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
        operation.apply(criteriaBuilder, criteriaQuery, selections);
    }

    private static ProjectionOperation projectionOperation(Class<?> findType, Class<?> entityType) {
        if (findType == Tuple.class) {
            return (criteriaBuilder, criteriaQuery, selections) -> criteriaQuery.multiselect(selections);
        }
        if (findType == Object[].class) {
            return (criteriaBuilder, criteriaQuery, selections) -> {
                CompoundSelection<Object[]> array = criteriaBuilder.array(selections);
                criteriaQuery.select(TypeConvert.cast(array));
            };
        }
        return (criteriaBuilder, criteriaQuery, selections) -> {
            CompoundSelection<?> construct = criteriaBuilder.construct(entityType, selections);
            criteriaQuery.select(TypeConvert.cast(construct));
        };
    }
}
