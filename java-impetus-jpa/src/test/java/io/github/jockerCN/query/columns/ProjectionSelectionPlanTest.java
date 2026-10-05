package io.github.jockerCN.query.columns;

import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.metadata.EntityMetadata;
import io.github.jockerCN.jpa.metadata.JpaAnnotationUtils;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCompoundSelection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProjectionSelectionPlanTest {

    static Stream<Arguments> projectionTypes() {
        return Stream.of(
                Arguments.of(Tuple.class, 1, "tuple"),
                Arguments.of(Tuple.class, 2, "tuple"),
                Arguments.of(Object[].class, 1, "array"),
                Arguments.of(Object[].class, 2, "array"),
                Arguments.of(Object.class, 1, "scalar"),
                Arguments.of(Object.class, 2, "array"),
                Arguments.of(View.class, 2, "construct"),
                Arguments.of(String[].class, 2, "typedArray"));
    }

    @ParameterizedTest
    @MethodSource("projectionTypes")
    void selectsUsingTheCurrentResultTypeAndPreservesColumnOrder(Class<?> resultType, int size, String operation) {
        EntityMetadata metadata = new EntityMetadata(View.class, JpaAnnotationUtils.validateAnnotationsOnFields(Param.class));
        Param param = new Param();
        param.columns = SelectColumn.ofNames("first", "second").stream().limit(size).toList();
        HibernateCriteriaBuilder builder = mock(HibernateCriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Root<?> root = mock(Root.class);
        Path<?> first = mock(Path.class);
        Path<?> second = mock(Path.class);
        JpaCompoundSelection<?> compound = mock(JpaCompoundSelection.class);
        doReturn(resultType).when(query).getResultType();
        doReturn(first).when(root).get("first");
        doReturn(second).when(root).get("second");
        doReturn(first).when(first).alias("first");
        doReturn(second).when(second).alias("second");
        doReturn(compound).when(builder).tuple(any(Selection[].class));
        doReturn(compound).when(builder).array(any(Selection[].class));
        doReturn(compound).when(builder).array(eq(resultType), any(Selection[].class));
        doReturn(compound).when(builder).construct(eq(resultType), any(Selection[].class));

        metadata.buildCriteriaQuery(builder, query, root, param);

        if (operation.equals("scalar")) {
            verify(query).select(TypeConvert.cast(first));
            verifyNoInteractions(builder);
        } else {
            ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
            switch (operation) {
                case "tuple" -> verify(builder).tuple(selections.capture());
                case "array" -> verify(builder).array(selections.capture());
                case "construct" -> verify(builder).construct(eq(resultType), selections.capture());
                case "typedArray" -> verify(builder).array(eq(resultType), selections.capture());
                default -> throw new AssertionError(operation);
            }
            assertThat(selections.getValue()).containsExactly(size == 1
                    ? new Selection<?>[]{first} : new Selection<?>[]{first, second});
            verify(query).select(TypeConvert.cast(compound));
        }
    }

    static class Param {
        @Columns
        List<SelectColumn> columns;
    }

    @SuppressWarnings("unused") // Criteria constructor-projection fixture; no direct accessor calls are needed.
    record View(String first, String second) { }
}
