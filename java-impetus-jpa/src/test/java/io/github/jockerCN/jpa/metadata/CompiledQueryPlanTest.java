package io.github.jockerCN.jpa.metadata;

import io.github.jockerCN.jpa.annotation.*;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.query.operator.HavingOperatorEnum;
import io.github.jockerCN.jpa.query.operator.RelatedOperatorEnum;
import io.github.jockerCN.jpa.query.operator.SqlFunctionEnum;
import io.github.jockerCN.jpa.query.model.OderByCondition;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompiledQueryPlanTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void executesPrecompiledWhereShapeAndPagingOperations() {
        EntityMetadata metadata = metadata(ExecutableQueryParam.class);
        ExecutableQueryParam queryParam = new ExecutableQueryParam();
        queryParam.id = 7L;
        queryParam.distinct = true;
        queryParam.orderBy = Set.of("id");
        queryParam.limit = 50;
        queryParam.page = 2;
        queryParam.pageSize = 10;

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        Order order = mock(Order.class);
        TypedQuery typedQuery = mock(TypedQuery.class);

        when(root.get("id")).thenReturn(idPath);
        when(criteriaBuilder.equal(idPath, 7L)).thenReturn(predicate);
        when(criteriaBuilder.desc(idPath)).thenReturn(order);

        assertThat(metadata.buildPersistenceList(criteriaBuilder, root, queryParam))
                .containsExactly(predicate);
        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);
        metadata.buildLimitAndPage(typedQuery, queryParam);

        verify(criteriaQuery).distinct(true);
        verify(criteriaBuilder).desc(idPath);
        ArgumentCaptor<java.util.List<Order>> orders = ArgumentCaptor.forClass(java.util.List.class);
        verify(criteriaQuery).orderBy(orders.capture());
        assertThat(orders.getValue()).containsExactly(order);

        InOrder typedQueryOrder = inOrder(typedQuery);
        typedQueryOrder.verify(typedQuery).setMaxResults(50);
        typedQueryOrder.verify(typedQuery).setFirstResult(20);
        typedQueryOrder.verify(typedQuery).setMaxResults(10);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void executesTheProjectionStrategySelectedWhileMetadataIsCompiled() {
        EntityMetadata metadata = metadata(ObjectArrayQueryParam.class);
        ObjectArrayQueryParam queryParam = new ObjectArrayQueryParam();
        queryParam.columns = Set.of(SelectColumn.of("id"));

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        CompoundSelection<Object[]> arraySelection = mock(CompoundSelection.class);

        when(root.get("id")).thenReturn(idPath);
        when(idPath.alias("id")).thenReturn(idPath);
        when(criteriaBuilder.array(any(Selection[].class))).thenReturn(arraySelection);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaBuilder).array(selections.capture());
        assertThat(selections.getValue()).containsExactly(idPath);
        verify(root).get("id");
        verify(idPath).alias("id");
        verify(criteriaQuery).select(arraySelection);
        verify(criteriaQuery, never()).multiselect(any(Selection[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void keepsTupleProjectionAsThePrecompiledDefaultStrategy() {
        EntityMetadata metadata = metadata(TupleQueryParam.class);
        TupleQueryParam queryParam = new TupleQueryParam();
        queryParam.columns = Set.of(SelectColumn.of("id"));

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        when(root.get("id")).thenReturn(idPath);
        when(idPath.alias("id")).thenReturn(idPath);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(idPath);
        verify(criteriaBuilder, never()).array(any(Selection[].class));
        verify(criteriaBuilder, never()).construct(any(), any(Selection[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void keepsEntityConstructionAsThePrecompiledProjectionStrategy() {
        EntityMetadata metadata = metadata(ConstructorQueryParam.class);
        ConstructorQueryParam queryParam = new ConstructorQueryParam();
        queryParam.columns = Set.of(SelectColumn.of("id"));

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        CompoundSelection<TestEntity> constructorSelection = mock(CompoundSelection.class);
        when(root.get("id")).thenReturn(idPath);
        when(idPath.alias("id")).thenReturn(idPath);
        when(criteriaBuilder.construct(eq(TestEntity.class), any(Selection[].class)))
                .thenReturn(constructorSelection);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaBuilder).construct(eq(TestEntity.class), selections.capture());
        assertThat(selections.getValue()).containsExactly(idPath);
        verify(criteriaQuery).select(constructorSelection);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appliesQueryShapeStagesAndOrderedFieldsPredictably() {
        EntityMetadata metadata = metadata(OrderedShapeQueryParam.class);
        OrderedShapeQueryParam queryParam = new OrderedShapeQueryParam();
        queryParam.orderBy = List.of("second", "id");
        queryParam.groupBy = List.of("id", "second");
        queryParam.distinct = true;
        queryParam.columns = List.of(SelectColumn.of("id"), SelectColumn.of("second"));

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        Path secondPath = mock(Path.class);
        Order secondOrder = mock(Order.class);
        Order idOrder = mock(Order.class);
        when(root.get("id")).thenReturn(idPath);
        when(root.get("second")).thenReturn(secondPath);
        when(idPath.alias("id")).thenReturn(idPath);
        when(secondPath.alias("second")).thenReturn(secondPath);
        when(criteriaBuilder.asc(secondPath)).thenReturn(secondOrder);
        when(criteriaBuilder.asc(idPath)).thenReturn(idOrder);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        InOrder stageOrder = inOrder(criteriaQuery);
        stageOrder.verify(criteriaQuery).multiselect(any(Selection[].class));
        stageOrder.verify(criteriaQuery).distinct(true);
        stageOrder.verify(criteriaQuery).groupBy(any(List.class));
        stageOrder.verify(criteriaQuery).orderBy(any(List.class));

        ArgumentCaptor<List<Expression<?>>> groups = ArgumentCaptor.forClass(List.class);
        verify(criteriaQuery).groupBy(groups.capture());
        assertThat(groups.getValue()).containsExactly(idPath, secondPath);
        ArgumentCaptor<List<Order>> orders = ArgumentCaptor.forClass(List.class);
        verify(criteriaQuery).orderBy(orders.capture());
        assertThat(orders.getValue()).containsExactly(secondOrder, idOrder);
    }

    @Test
    void keepsSelectColumnBuilderInsertionOrder() {
        Set<SelectColumn> columns = SelectColumn.SetBuilder.create()
                .column("second").add()
                .column("id").add()
                .build();

        assertThat(columns.stream().map(SelectColumn::getName)).containsExactly("second", "id");
        assertThat(SelectColumn.ofNames("second", "id"))
                .extracting(SelectColumn::getName)
                .containsExactly("second", "id");

        SelectColumn base = SelectColumn.of("id");
        SelectColumn conditional = base.when(param -> false);
        assertThat(base.includes(new Object())).isTrue();
        assertThat(conditional.includes(new Object())).isFalse();
        assertThat(SelectColumn.SetBuilder.create()
                .column("id").when(param -> false).add().build().iterator().next().includes(new Object()))
                .isFalse();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void conditionallySelectsEntityFieldOrTypedNullWithoutReadingHiddenField() {
        EntityMetadata metadata = metadata(ConditionalColumnsQueryParam.class);
        ConditionalColumnsQueryParam queryParam = new ConditionalColumnsQueryParam();
        queryParam.columns = List.of(
                SelectColumn.of("phone").when(param -> ((ConditionalColumnsQueryParam) param).canViewPhone),
                SelectColumn.nullValue("phone", String.class)
                        .when(param -> !((ConditionalColumnsQueryParam) param).canViewPhone)
        );

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path<String> phonePath = mock(Path.class);
        Expression<String> nullExpression = mock(Expression.class);
        when(root.get("phone")).thenReturn(phonePath);
        when(phonePath.alias("phone")).thenReturn(phonePath);
        when(criteriaBuilder.nullLiteral(String.class)).thenReturn(nullExpression);
        when(nullExpression.alias("phone")).thenReturn(nullExpression);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(nullExpression);
        verify(root, never()).get("phone");

        clearInvocations(criteriaBuilder, criteriaQuery, root, phonePath, nullExpression);
        queryParam.canViewPhone = true;
        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(phonePath);
        verify(root).get("phone");
        verify(criteriaBuilder, never()).nullLiteral(String.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void selectsNonNullConstantWithoutAnEntityProperty() {
        EntityMetadata metadata = metadata(ConditionalColumnsQueryParam.class);
        ConditionalColumnsQueryParam queryParam = new ConditionalColumnsQueryParam();
        queryParam.columns = List.of(SelectColumn.constant("phone", "hidden"));
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Expression<String> literal = mock(Expression.class);
        when(criteriaBuilder.literal("hidden")).thenReturn(literal);
        when(literal.alias("phone")).thenReturn(literal);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(literal);
        verifyNoInteractions(root);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preservesFunctionArgumentsThroughFactoriesAndBuilder() {
        EntityMetadata metadata = metadata(ConditionalColumnsQueryParam.class);
        ConditionalColumnsQueryParam queryParam = new ConditionalColumnsQueryParam();
        queryParam.columns = List.of(
                SelectColumn.of("amount", "roundedZero", SqlFunctionEnum.round, 0)
                        .when(param -> true),
                SelectColumn.builder().name("amount").function(SqlFunctionEnum.round, 1)
                        .alias("roundedOne").build()
        );

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path amountPath = mock(Path.class);
        Expression roundedZero = mock(Expression.class);
        Expression roundedOne = mock(Expression.class);
        when(root.get("amount")).thenReturn(amountPath);
        when(criteriaBuilder.round(amountPath, 0)).thenReturn(roundedZero);
        when(criteriaBuilder.round(amountPath, 1)).thenReturn(roundedOne);
        when(roundedZero.alias("roundedZero")).thenReturn(roundedZero);
        when(roundedOne.alias("roundedOne")).thenReturn(roundedOne);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(roundedZero, roundedOne);
        verify(criteriaBuilder).round(amountPath, 0);
        verify(criteriaBuilder).round(amountPath, 1);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void resolvesDynamicColumnValueFromTheSameParamOnEveryQuery() {
        EntityMetadata metadata = metadata(ConditionalColumnsQueryParam.class);
        ConditionalColumnsQueryParam queryParam = new ConditionalColumnsQueryParam();
        queryParam.dynamicValue = "first";
        AtomicInteger calls = new AtomicInteger();
        queryParam.columns = List.of(SelectColumn.dynamic("computed", String.class, param -> {
            assertThat(param).isSameAs(queryParam);
            calls.incrementAndGet();
            return ((ConditionalColumnsQueryParam) param).dynamicValue;
        }));

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Expression<String> literal = mock(Expression.class);
        Expression<String> nullExpression = mock(Expression.class);
        when(criteriaBuilder.literal("first")).thenReturn(literal);
        when(criteriaBuilder.nullLiteral(String.class)).thenReturn(nullExpression);
        when(literal.alias("computed")).thenReturn(literal);
        when(nullExpression.alias("computed")).thenReturn(nullExpression);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);
        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(literal);

        clearInvocations(criteriaBuilder, criteriaQuery, root, literal, nullExpression);
        queryParam.dynamicValue = null;
        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(nullExpression);
        assertThat(calls).hasValue(2);
        verifyNoInteractions(root);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void buildsRowLevelCaseExpressionUsingTheCurrentCriteriaTree() {
        EntityMetadata metadata = metadata(ConditionalColumnsQueryParam.class);
        ConditionalColumnsQueryParam queryParam = new ConditionalColumnsQueryParam();
        queryParam.columns = List.of(SelectColumn.expression("phone", (criteriaBuilder, root, param) -> {
            assertThat(param).isSameAs(queryParam);
            return criteriaBuilder.<String>selectCase()
                    .when(criteriaBuilder.isTrue(root.get("visible")), root.get("phone"))
                    .otherwise(criteriaBuilder.nullLiteral(String.class));
        }).when(param -> true));

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        CriteriaBuilder.Case<String> caseExpression = mock(CriteriaBuilder.Case.class);
        Path<Boolean> visiblePath = mock(Path.class);
        Path<String> phonePath = mock(Path.class);
        Predicate visiblePredicate = mock(Predicate.class);
        Expression<String> nullExpression = mock(Expression.class);
        when(criteriaBuilder.<String>selectCase()).thenReturn(caseExpression);
        when(root.<Boolean>get("visible")).thenReturn(visiblePath);
        when(criteriaBuilder.isTrue(visiblePath)).thenReturn(visiblePredicate);
        when(root.<String>get("phone")).thenReturn(phonePath);
        when(criteriaBuilder.nullLiteral(String.class)).thenReturn(nullExpression);
        when(caseExpression.when(visiblePredicate, phonePath)).thenReturn(caseExpression);
        when(caseExpression.otherwise(nullExpression)).thenReturn(caseExpression);
        when(caseExpression.alias("phone")).thenReturn(caseExpression);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(caseExpression);
        verify(criteriaBuilder).selectCase();
        verify(caseExpression).when(visiblePredicate, phonePath);
        verify(caseExpression).otherwise(nullExpression);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void keepsLegacyQueryExpressionSetterWorking() {
        EntityMetadata metadata = metadata(ConditionalColumnsQueryParam.class);
        ConditionalColumnsQueryParam queryParam = new ConditionalColumnsQueryParam();
        SelectColumn column = SelectColumn.of("phone");
        column.setQueryExpression((criteriaBuilder, root) -> criteriaBuilder.literal("overridden"));
        queryParam.columns = List.of(column);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Expression<String> literal = mock(Expression.class);
        when(criteriaBuilder.literal("overridden")).thenReturn(literal);
        when(literal.alias("phone")).thenReturn(literal);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        ArgumentCaptor<Selection<?>[]> selections = ArgumentCaptor.forClass(Selection[].class);
        verify(criteriaQuery).multiselect(selections.capture());
        assertThat(selections.getValue()).containsExactly(literal);
        verifyNoInteractions(root);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void rejectsAProjectionWhoseConditionsRemoveEveryColumn() {
        EntityMetadata metadata = metadata(ConditionalColumnsQueryParam.class);
        ConditionalColumnsQueryParam queryParam = new ConditionalColumnsQueryParam();
        queryParam.columns = List.of(SelectColumn.of("phone").when(param -> false));
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);

        assertThatThrownBy(() -> metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Columns has no selected fields");
        verifyNoInteractions(root, criteriaQuery);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void doesNotReadQueryShapeAnnotationsAfterThePlanIsCompiled() throws NoSuchFieldException {
        AtomicBoolean executionStarted = new AtomicBoolean();
        Columns columns = guardedAnnotation(
                Columns.class,
                Map.of("value", Object[].class),
                executionStarted
        );
        OrderBy orderBy = guardedAnnotation(
                OrderBy.class,
                Map.of("value", OderByCondition.ASC),
                executionStarted
        );
        Field columnsField = GuardedShapeQueryParam.class.getDeclaredField("columns");
        Field orderByField = GuardedShapeQueryParam.class.getDeclaredField("orderBy");
        EntityMetadata metadata = new EntityMetadata(
                TestEntity.class,
                Map.of(columnsField, columns, orderByField, orderBy)
        );
        executionStarted.set(true);

        GuardedShapeQueryParam queryParam = new GuardedShapeQueryParam();
        queryParam.columns = Set.of(SelectColumn.of("id"));
        queryParam.orderBy = Set.of("id");
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        CompoundSelection<Object[]> arraySelection = mock(CompoundSelection.class);
        Order order = mock(Order.class);
        when(root.get("id")).thenReturn(idPath);
        when(idPath.alias("id")).thenReturn(idPath);
        when(criteriaBuilder.array(any(Selection[].class))).thenReturn(arraySelection);
        when(criteriaBuilder.asc(idPath)).thenReturn(order);

        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);

        verify(criteriaBuilder).array(any(Selection[].class));
        verify(criteriaBuilder).asc(idPath);
        verify(criteriaBuilder, never()).desc(idPath);
    }

    @Test
    @SuppressWarnings({"rawtypes"})
    void keepsRuntimeDataGuardsAsNoOpOperations() {
        EntityMetadata metadata = metadata(ExecutableQueryParam.class);
        ExecutableQueryParam queryParam = new ExecutableQueryParam();
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        TypedQuery typedQuery = mock(TypedQuery.class);

        assertThat(metadata.buildPersistenceList(criteriaBuilder, root, queryParam)).isEmpty();
        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);
        metadata.buildLimitAndPage(typedQuery, queryParam);

        verifyNoInteractions(criteriaBuilder, criteriaQuery, root, typedQuery);
    }

    @Test
    @SuppressWarnings({"rawtypes"})
    void doesNotReadWhereAnnotationAfterThePlanIsCompiled() throws NoSuchFieldException {
        AtomicBoolean executionStarted = new AtomicBoolean();
        Equals annotation = guardedAnnotation(
                Equals.class,
                Map.of("value", "id"),
                executionStarted
        );
        Field field = GuardedQueryParam.class.getDeclaredField("value");
        EntityMetadata metadata = new EntityMetadata(TestEntity.class, Map.of(field, annotation));
        executionStarted.set(true);

        GuardedQueryParam queryParam = new GuardedQueryParam();
        queryParam.value = 11L;
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("id")).thenReturn(idPath);
        when(criteriaBuilder.equal(idPath, 11L)).thenReturn(predicate);

        assertThat(metadata.buildPersistenceList(criteriaBuilder, root, queryParam))
                .containsExactly(predicate);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void doesNotReadHavingAnnotationAfterThePlanIsCompiled() throws NoSuchFieldException {
        AtomicBoolean executionStarted = new AtomicBoolean();
        Having annotation = guardedAnnotation(
                Having.class,
                Map.of(
                        "value", "id",
                        "operator", HavingOperatorEnum.equal,
                        "function", SqlFunctionEnum.round,
                        "round", 2,
                        "related", RelatedOperatorEnum.AND
                ),
                executionStarted
        );
        Field field = GuardedHavingQueryParam.class.getDeclaredField("value");
        EntityMetadata metadata = new EntityMetadata(TestEntity.class, Map.of(field, annotation));
        executionStarted.set(true);

        GuardedHavingQueryParam queryParam = new GuardedHavingQueryParam();
        queryParam.value = 12;
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        Path roundedPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("id")).thenReturn(idPath);
        when(criteriaBuilder.round(idPath, 2)).thenReturn(roundedPath);
        when(criteriaBuilder.equal(roundedPath, 12)).thenReturn(predicate);

        assertThat(metadata.buildHavingPersistence(criteriaBuilder, root, queryParam))
                .containsExactly(predicate);
    }

    @Test
    @SuppressWarnings({"rawtypes"})
    void compilesHavingOrderAndMergeOperationBeforeExecution() {
        EntityMetadata metadata = metadata(HavingGroupQueryParam.class);
        HavingGroupQueryParam queryParam = new HavingGroupQueryParam();
        queryParam.first = 3;
        queryParam.second = 8;

        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        Predicate firstPredicate = mock(Predicate.class);
        Predicate secondPredicate = mock(Predicate.class);
        Predicate mergedPredicate = mock(Predicate.class);
        when(root.get("id")).thenReturn(idPath);
        when(criteriaBuilder.equal(idPath, 3)).thenReturn(firstPredicate);
        when(criteriaBuilder.equal(idPath, 8)).thenReturn(secondPredicate);
        when(criteriaBuilder.or(firstPredicate, secondPredicate)).thenReturn(mergedPredicate);

        assertThat(metadata.buildHavingPersistence(criteriaBuilder, root, queryParam))
                .containsExactly(mergedPredicate);

        InOrder operationOrder = inOrder(criteriaBuilder);
        operationOrder.verify(criteriaBuilder).equal(idPath, 3);
        operationOrder.verify(criteriaBuilder).equal(idPath, 8);
        operationOrder.verify(criteriaBuilder).or(firstPredicate, secondPredicate);
    }

    @Test
    void reusesTheMetadataCompiledForAQueryParameterClass() {
        CachedQueryParam firstQueryParam = new CachedQueryParam();
        JpaQuery jpaQuery = mock(JpaQuery.class);
        doReturn(TestEntity.class).when(jpaQuery).value();

        JpaQueryEntityProcess.createQueryParam(jpaQuery, CachedQueryParam.class);
        EntityMetadata firstMetadata = JpaQueryEntityProcess.getEntityMetadata(firstQueryParam);

        CachedQueryParam secondQueryParam = new CachedQueryParam();
        assertThatCode(() -> JpaQueryEntityProcess.createQueryParam(jpaQuery, CachedQueryParam.class))
                .doesNotThrowAnyException();
        assertThat(JpaQueryEntityProcess.getEntityMetadata(secondQueryParam))
                .isSameAs(firstMetadata);
    }

    public static EntityMetadata metadata(Class<?> queryParamType) {
        return new EntityMetadata(
                TestEntity.class,
                JpaAnnotationUtils.validateAnnotationsOnFields(queryParamType)
        );
    }

    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A guardedAnnotation(Class<A> annotationType,
                                                               Map<String, Object> values,
                                                               AtomicBoolean executionStarted) {
        return (A) Proxy.newProxyInstance(
                annotationType.getClassLoader(),
                new Class<?>[]{annotationType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "@" + annotationType.getName();
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
                    if (executionStarted.get()) {
                        throw new AssertionError("annotation was read while executing the compiled query plan: " + method.getName());
                    }
                    if (method.getName().equals("annotationType")) {
                        return annotationType;
                    }
                    return values.getOrDefault(method.getName(), method.getDefaultValue());
                }
        );
    }

    public static final class TestEntity {

        public Long id;
    }

    public static final class ExecutableQueryParam {

        @Equals
        public Long id;

        @Distinct
        public Boolean distinct;

        @OrderBy(OderByCondition.DESC)
        public Set<String> orderBy;

        @Limit
        public Integer limit;

        @Page
        public Integer page;

        @PageSize
        public Integer pageSize;
    }

    public static final class ObjectArrayQueryParam {

        @Columns(Object[].class)
        public Set<SelectColumn> columns;
    }

    public static final class TupleQueryParam {

        @Columns
        public Set<SelectColumn> columns;
    }

    public static final class ConstructorQueryParam {

        @Columns(TestEntity.class)
        public Set<SelectColumn> columns;
    }

    public static final class OrderedShapeQueryParam {

        @OrderBy
        public List<String> orderBy;

        @GroupBy
        public List<String> groupBy;

        @Distinct
        public Boolean distinct;

        @Columns
        public List<SelectColumn> columns;
    }

    public static final class ConditionalColumnsQueryParam {

        @Columns
        public List<SelectColumn> columns;

        public boolean canViewPhone;

        public String dynamicValue;
    }

    public static final class GuardedQueryParam {

        public Long value;
    }

    public static final class GuardedHavingQueryParam {

        public Integer value;
    }

    public static final class GuardedShapeQueryParam {

        public Set<SelectColumn> columns;

        public Set<String> orderBy;
    }

    public static final class HavingGroupQueryParam {

        @Having(value = "id", group = 1, sort = 0, operator = HavingOperatorEnum.equal)
        public Integer first;

        @Having(value = "id", group = 1, sort = 1, operator = HavingOperatorEnum.equal, related = RelatedOperatorEnum.OR)
        public Integer second;
    }

    public static final class CachedQueryParam {
    }
}
