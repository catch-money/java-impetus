package io.github.jockerCN.query.value;

import io.github.jockerCN.JpaTestBase;
import io.github.jockerCN.jpa.DefaultJpaQuery;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.QueryDefault;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.query.value.QueryParamProcessor;
import io.github.jockerCN.jpa.query.value.QueryValueProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryManagerLifecycleTest extends JpaTestBase {

    @Autowired
    private RecordingProcessor processor;

    @Autowired
    private RecordingProvider provider;

    @Test
    @SuppressWarnings({"unchecked"})
    void usesTheSameParamThroughQueryAndCountWithoutCachingFieldValues() {
        EntityManager entityManager = mock(EntityManager.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery<TestEntity> entityQuery = mock(CriteriaQuery.class);
        CriteriaQuery<Long> countQuery = mock(CriteriaQuery.class);
        Root<TestEntity> root = mock(Root.class);
        Path<Long> ownerPath = mock(Path.class);
        Expression<Long> countExpression = mock(Expression.class);
        Predicate firstPredicate = mock(Predicate.class);
        Predicate secondPredicate = mock(Predicate.class);
        Predicate explicitPredicate = mock(Predicate.class);
        TypedQuery<TestEntity> listQuery = mock(TypedQuery.class);
        TypedQuery<Long> countTypedQuery = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(TestEntity.class)).thenReturn(entityQuery);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
        when(entityQuery.from(TestEntity.class)).thenReturn(root);
        when(countQuery.from(TestEntity.class)).thenReturn(root);
        when(countQuery.getRoots()).thenReturn(Set.of(root));
        when(root.<Long>get("ownerId")).thenReturn(ownerPath);
        when(criteriaBuilder.equal(ownerPath, 1L)).thenReturn(firstPredicate);
        when(criteriaBuilder.equal(ownerPath, 2L)).thenReturn(secondPredicate);
        when(criteriaBuilder.equal(ownerPath, 99L)).thenReturn(explicitPredicate);
        when(criteriaBuilder.count(root)).thenReturn(countExpression);
        when(entityManager.createQuery(entityQuery)).thenReturn(listQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(countTypedQuery);
        when(listQuery.getResultList()).thenReturn(List.of());
        when(countTypedQuery.getSingleResult()).thenReturn(2L);

        DefaultJpaQuery queryManager = new DefaultJpaQuery();
        ReflectionTestUtils.setField(queryManager, "manager", entityManager);
        Param first = new Param();

        assertThat(queryManager.queryList(first)).isEmpty();
        assertThat(queryManager.count(first)).isEqualTo(2L);
        assertThat(first.pass).isEqualTo(2);
        assertThat(first.ownerId).isNull();
        assertThat(processor.seen).containsExactly(first, first);
        assertThat(provider.seen).containsExactly(first, first);
        assertThat(processor.events).containsExactly("process:1", "provide:1", "process:2", "provide:2");
        verify(criteriaBuilder).equal(ownerPath, 1L);
        verify(criteriaBuilder).equal(ownerPath, 2L);

        Param second = new Param();
        second.ownerId = 99L;
        assertThat(queryManager.queryList(second)).isEmpty();
        assertThat(second.pass).isEqualTo(1);
        assertThat(processor.seen).containsExactly(first, first, second);
        assertThat(provider.seen).containsExactly(first, first);
        verify(criteriaBuilder).equal(ownerPath, 99L);
    }

    @JpaQuery(value = TestEntity.class, processor = RecordingProcessor.class)
    static class Param {
        @Equals
        @QueryDefault(RecordingProvider.class)
        Long ownerId;
        int pass;
    }

    static class TestEntity {
        Long ownerId;
    }

    @Component
    public static class RecordingProcessor implements QueryParamProcessor {
        final List<Object> seen = new ArrayList<>();
        final List<String> events = new ArrayList<>();

        @Override
        public void process(Object queryParam) {
            Param param = (Param) queryParam;
            seen.add(queryParam);
            events.add("process:" + ++param.pass);
        }
    }

    @Component
    public static class RecordingProvider implements QueryValueProvider<Long> {
        final List<Object> seen = new ArrayList<>();
        private final RecordingProcessor processor;

        RecordingProvider(RecordingProcessor processor) {
            this.processor = processor;
        }

        @Override
        public Long provide(Object queryParam) {
            Param param = (Param) queryParam;
            seen.add(queryParam);
            processor.events.add("provide:" + param.pass);
            return (long) param.pass;
        }
    }

}
