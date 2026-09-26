package io.github.jockerCN.query;

import io.github.jockerCN.jpa.DefaultJpaQuery;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlainQueryParamTest {

    @Test
    @SuppressWarnings({"rawtypes"})
    void acceptsAStandaloneParameterClass() {
        JpaQueryEntityProcess.createQueryParam(StandaloneParam.class.getAnnotation(JpaQuery.class), StandaloneParam.class);
        StandaloneParam param = new StandaloneParam(9L);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("id")).thenReturn(idPath);
        when(criteriaBuilder.equal(idPath, 9L)).thenReturn(predicate);

        assertThat(JpaQueryEntityProcess.getEntityMetadata(param)
                .buildPersistenceList(criteriaBuilder, root, param)).containsExactly(predicate);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void registersAParameterWithoutFrameworkBaseOrNoArgConstructor() {
        JpaQueryEntityProcess.createQueryParam(PlainParam.class.getAnnotation(JpaQuery.class), PlainParam.class);
        PlainParam param = new PlainParam(7L, 1, 3);

        assertThat(JpaQueryEntityProcess.getEntityMetadata(param).getEntityType()).isEqualTo(PlainEntity.class);

        EntityManager entityManager = mock(EntityManager.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        TypedQuery typedQuery = mock(TypedQuery.class);
        PlainEntity entity = new PlainEntity();

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(PlainEntity.class)).thenReturn(criteriaQuery);
        when(criteriaQuery.from(PlainEntity.class)).thenReturn(root);
        when(root.get("id")).thenReturn(idPath);
        when(criteriaBuilder.equal(idPath, 7L)).thenReturn(predicate);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(entity));

        DefaultJpaQuery queryManager = new DefaultJpaQuery();
        ReflectionTestUtils.setField(queryManager, "manager", entityManager);

        assertThat(queryManager.queryList(param, PlainEntity.class)).containsExactly(entity);
        verify(criteriaBuilder).equal(idPath, 7L);

        InOrder paging = inOrder(typedQuery);
        paging.verify(typedQuery).setFirstResult(3);
        paging.verify(typedQuery).setMaxResults(3);
    }

    public static class CustomBase {

        @Page
        protected Integer page;

        @PageSize
        protected Integer pageSize;
    }

    @JpaQuery(PlainEntity.class)
    public static final class StandaloneParam {

        @Equals
        private final Long id;

        private StandaloneParam(Long id) {
            this.id = id;
        }
    }

    @JpaQuery(PlainEntity.class)
    public static final class PlainParam extends CustomBase {

        @Equals
        private final Long id;

        private PlainParam(Long id, Integer page, Integer pageSize) {
            this.id = id;
            this.page = page;
            this.pageSize = pageSize;
        }
    }

    @Entity
    public static class PlainEntity {

        @Id
        private Long id;
    }
}
