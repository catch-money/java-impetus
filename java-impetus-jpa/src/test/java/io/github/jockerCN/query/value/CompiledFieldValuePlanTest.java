package io.github.jockerCN.query.value;

import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.QueryDefault;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.metadata.EntityMetadata;
import io.github.jockerCN.jpa.metadata.FieldMetadata;
import io.github.jockerCN.jpa.metadata.JpaAnnotationUtils;
import io.github.jockerCN.jpa.metadata.JpaQueryEntityBuilder;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.jpa.query.value.QueryParamProcessor;
import io.github.jockerCN.jpa.query.value.QueryValueProvider;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CompiledFieldValuePlanTest {

    @Test
    void fieldMetadataInvokeKeepsReadingTheRawField() throws NoSuchFieldException {
        Field field = Param.class.getDeclaredField("ownerId");
        ReflectionUtils.makeAccessible(field);
        FieldMetadata metadata = JpaQueryEntityBuilder.buildFieldMetadata(
                field, field.getAnnotation(Equals.class)).orElseThrow();
        Param param = new Param();

        assertThat(metadata.getInvoke().apply(param)).isNull();
        param.ownerId = 12L;
        assertThat(metadata.getInvoke().apply(param)).isEqualTo(12L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void processorMutatesOriginalParamBeforeFieldReadersRun() {
        AtomicInteger ownerCalls = new AtomicInteger();
        AtomicInteger columnCalls = new AtomicInteger();
        EntityMetadata metadata = new EntityMetadata(TestEntity.class,
                JpaAnnotationUtils.validateAnnotationsOnFields(Param.class),
                type -> type == OwnerProvider.class ? (QueryValueProvider<Long>) param -> {
                    ownerCalls.incrementAndGet();
                    return 7L;
                } : type == ColumnsProvider.class ? (QueryValueProvider<List<SelectColumn>>) param -> {
                    columnCalls.incrementAndGet();
                    return List.of(SelectColumn.of("id"));
                } : new Processor(),
                Processor.class);
        Param param = new Param();
        metadata.processQueryParam(param);
        assertThat(param.ownerId).isEqualTo(8L);
        assertThat(param.columns).extracting(SelectColumn::getName).containsExactly("id");

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery cq = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path ownerPath = mock(Path.class);
        Path idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("ownerId")).thenReturn(ownerPath);
        when(root.get("id")).thenReturn(idPath);
        when(cb.equal(ownerPath, 8L)).thenReturn(predicate);
        when(idPath.alias("id")).thenReturn(idPath);

        assertThat(metadata.buildPersistenceList(cb, root, param)).containsExactly(predicate);
        metadata.buildCriteriaQuery(cb, cq, root, param);
        verify(cq).multiselect(any(jakarta.persistence.criteria.Selection[].class));
        assertThat(ownerCalls).hasValue(0);
        assertThat(columnCalls).hasValue(0);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void defaultProviderReadsSameParamOnlyWhenFieldIsNull() {
        AtomicInteger calls = new AtomicInteger();
        EntityMetadata metadata = new EntityMetadata(TestEntity.class,
                JpaAnnotationUtils.validateAnnotationsOnFields(Param.class),
                type -> type == OwnerProvider.class
                        ? (QueryValueProvider<Long>) param -> {
                            assertThat(param).isInstanceOf(Param.class);
                            calls.incrementAndGet();
                            return 7L;
                        } : type == ColumnsProvider.class ? new ColumnsProvider() : new Processor(),
                QueryParamProcessor.None.class);
        Param first = new Param();
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root root = mock(Root.class);
        Path ownerPath = mock(Path.class);
        Predicate defaultPredicate = mock(Predicate.class);
        Predicate explicitPredicate = mock(Predicate.class);
        when(root.get("ownerId")).thenReturn(ownerPath);
        when(cb.equal(ownerPath, 7L)).thenReturn(defaultPredicate);
        when(cb.equal(ownerPath, 12L)).thenReturn(explicitPredicate);

        assertThat(metadata.buildPersistenceList(cb, root, first)).containsExactly(defaultPredicate);
        assertThat(first.ownerId).isNull();
        assertThat(calls).hasValue(1);

        Param second = new Param();
        second.ownerId = 12L;
        assertThat(metadata.buildPersistenceList(cb, root, second)).containsExactly(explicitPredicate);
        assertThat(calls).hasValue(1);
    }

    static class TestEntity {
        Long id;
        Long ownerId;
    }

    static class Param {
        @Equals
        @QueryDefault(OwnerProvider.class)
        Long ownerId;

        @Columns
        @QueryDefault(ColumnsProvider.class)
        List<SelectColumn> columns;
    }

    public static class OwnerProvider implements QueryValueProvider<Long> {
        @Override
        public Long provide(Object queryParam) {
            return 7L;
        }
    }

    public static class ColumnsProvider implements QueryValueProvider<List<SelectColumn>> {
        @Override
        public List<SelectColumn> provide(Object queryParam) {
            return List.of(SelectColumn.of("id"));
        }
    }

    public static class Processor implements QueryParamProcessor {
        @Override
        public void process(Object queryParam) {
            Param param = (Param) queryParam;
            param.ownerId = 8L;
            param.columns = List.of(SelectColumn.of("id"));
        }
    }
}
