package io.github.jockerCN.query.where;

import io.github.jockerCN.jpa.annotation.where.*;
import io.github.jockerCN.jpa.exception.JpaProcessException;
import io.github.jockerCN.jpa.metadata.FieldMetadata;
import io.github.jockerCN.jpa.metadata.JpaAnnotationUtils;
import io.github.jockerCN.jpa.metadata.JpaQueryEntityBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhereAnnotationRegistryTest {

    @Test
    void whereAnnotationIsDiscoveredAndCompiled() throws NoSuchFieldException {
        Field field = QueryParam.class.getDeclaredField("filter");
        assertTrue(JpaAnnotationUtils.jpaAnnotations.contains(ILike.class));

        FieldMetadata metadata = JpaQueryEntityBuilder.buildFieldMetadata(field, field.getAnnotation(ILike.class))
                .orElseThrow();
        assertEquals("tradeState", metadata.getAnnotationValue());
    }

    @Test
    void whereAnnotationKeepsItsFieldTypeValidation() throws NoSuchFieldException {
        Field field = QueryParam.class.getDeclaredField("invalid");

        assertThrows(JpaProcessException.class,
                () -> JpaQueryEntityBuilder.buildFieldMetadata(field, field.getAnnotation(ILike.class)));
    }

    @Test
    void allWhereAnnotationsAreRegisteredAndEqualsCompiles() throws NoSuchFieldException {
        Field field = QueryParam.class.getDeclaredField("id");

        assertTrue(JpaAnnotationUtils.jpaAnnotations.containsAll(Set.of(
                BetweenAnd.class, Equals.class, NoEquals.class, GE.class, GT.class,
                IN.class, NotIn.class, IsNotNull.class, IsNull.class, IsTrueOrFalse.class,
                LE.class, LT.class, Like.class, ILike.class, NotLike.class, NotILike.class)));
        assertTrue(JpaQueryEntityBuilder.buildFieldMetadata(field, field.getAnnotation(Equals.class)).isPresent());
    }

    @Test
    void whereAnnotationStillParticipatesInSingleAnnotationRule() {
        assertThrows(IllegalArgumentException.class,
                () -> JpaAnnotationUtils.validateAnnotationsOnFields(InvalidQueryParam.class));
    }

    public static class QueryParam {
        @ILike("tradeState")
        public String filter;

        @ILike
        public Long invalid;

        @Equals
        public Long id;
    }

    public static class InvalidQueryParam {
        @ILike
        @Equals
        public String filter;
    }
}
