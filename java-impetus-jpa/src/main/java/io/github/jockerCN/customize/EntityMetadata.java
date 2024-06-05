package io.github.jockerCN.customize;

import lombok.Getter;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Getter
public class EntityMetadata {

    /**
     * @Entity 注解标注的实体类 类型
     */
    private final Class<?> entityType;

    /**
     * 查询参数类 有限定注解的字段
     */
    private final Map<String, FieldMetadata> fieldsMetadataMap;


    public EntityMetadata(Class<?> entityType) {
        this.entityType = entityType;
        this.fieldsMetadataMap = new HashMap<>();
        ReflectionUtils.doWithFields(this.entityType, (filed)->{
            final Annotation[] annotations = filed.getAnnotations();
        });

    }

    public static EntityMetadata build(Class<?> queryType) {
        Objects.requireNonNull(queryType, "EntityMetadata queryType is null");
        return new EntityMetadata(queryType);
    }
}
