package io.github.jockerCN.jpa.metadata;

import io.github.jockerCN.jpa.annotation.where.*;
import io.github.jockerCN.jpa.query.model.QueryPair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess.validateFieldType;

/** Startup-only discovery and compiler registry for WHERE annotations. */
final class WhereAnnotationRegistry {

    private static final Map<Class<? extends Annotation>, BiFunction<Field, Annotation, FieldMetadata>> COMPILERS =
            Map.ofEntries(Map.entry(BetweenAnd.class, (field, annotation) -> {
                BetweenAnd betweenAnd = (BetweenAnd) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                validateFieldType(field, "@BetweenAnd", QueryPair.class);
                metadata.fillAnnotationValue(betweenAnd.value());
                metadata.betweenAndInit();
                return metadata;
            }), Map.entry(Equals.class, (field, annotation) -> {
                Equals equals = (Equals) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(equals.value());
                metadata.equalsInit();
                return metadata;
            }), Map.entry(NoEquals.class, (field, annotation) -> {
                NoEquals noEquals = (NoEquals) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(noEquals.value());
                metadata.noEqualsInit();
                return metadata;
            }), Map.entry(GE.class, (field, annotation) -> {
                GE ge = (GE) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(ge.value());
                metadata.geInit();
                return metadata;
            }), Map.entry(GT.class, (field, annotation) -> {
                GT gt = (GT) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(gt.value());
                metadata.gtInit();
                return metadata;
            }), Map.entry(IN.class, (field, annotation) -> {
                IN in = (IN) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                validateFieldType(field, "@IN", Collection.class);
                metadata.fillAnnotationValue(in.value());
                metadata.inInit();
                return metadata;
            }), Map.entry(NotIn.class, (field, annotation) -> {
                NotIn notIn = (NotIn) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                validateFieldType(field, "@NotIn", Collection.class);
                metadata.fillAnnotationValue(notIn.value());
                metadata.notInInit();
                return metadata;
            }), Map.entry(IsNotNull.class, (field, annotation) -> {
                IsNotNull isNotNull = (IsNotNull) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                validateFieldType(field, "@IsNotNull", Boolean.class);
                metadata.fillAnnotationValue(isNotNull.value());
                metadata.isNotNullInit();
                return metadata;
            }), Map.entry(IsNull.class, (field, annotation) -> {
                IsNull isNull = (IsNull) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                validateFieldType(field, "@IsNull", Boolean.class);
                metadata.fillAnnotationValue(isNull.value());
                metadata.isNullInit();
                return metadata;
            }), Map.entry(IsTrueOrFalse.class, (field, annotation) -> {
                IsTrueOrFalse isTrueOrFalse = (IsTrueOrFalse) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                validateFieldType(field, "@IsTrueOrFalse", Boolean.class);
                metadata.fillAnnotationValue(isTrueOrFalse.value());
                metadata.isTrueOrFalseInit();
                return metadata;
            }), Map.entry(LE.class, (field, annotation) -> {
                LE le = (LE) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(le.value());
                metadata.leInit();
                return metadata;
            }), Map.entry(Like.class, (field, annotation) -> {
                Like like = (Like) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(like.value());
                metadata.likeInit();
                return metadata;
            }), Map.entry(ILike.class, (field, annotation) -> {
                ILike like = (ILike) annotation;
                validateFieldType(field, "@ILike", String.class);
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(like.value());
                metadata.iLikeInit();
                return metadata;
            }), Map.entry(LT.class, (field, annotation) -> {
                LT lt = (LT) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(lt.value());
                metadata.ltInit();
                return metadata;
            }), Map.entry(NotLike.class, (field, annotation) -> {
                NotLike notLike = (NotLike) annotation;
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(notLike.value());
                metadata.notLikeInit();
                return metadata;
            }), Map.entry(NotILike.class, (field, annotation) -> {
                NotILike notILike = (NotILike) annotation;
                validateFieldType(field, "@NotILike", String.class);
                FieldMetadata metadata = new FieldMetadata(field, annotation);
                metadata.fillAnnotationValue(notILike.value());
                metadata.notILikeInit();
                return metadata;
            }));

    private WhereAnnotationRegistry() {
    }

    static Set<Class<? extends Annotation>> annotationTypes() {
        return COMPILERS.keySet();
    }

    static Optional<FieldMetadata> compile(Field field, Annotation annotation) {
        return Optional.ofNullable(COMPILERS.get(annotation.annotationType()))
                .map(compiler -> compiler.apply(field, annotation));
    }
}
