package io.github.jockerCN.jackson;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.lang.annotation.*;

/**
 * 默认值注解，自动根据字段类型推断值类型
 * @author jokerCN
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonDeserialize(using = DefaultValueDeserializer.class)
public @interface DefaultValue {
    
    /**
     * 默认值，支持字符串形式，会自动转换为对应类型
     */
    String value();


    /**
     * 是否为JSON格式（用于复杂数据结构）
     * 如果为true，将使用Jackson解析JSON字符串
     * 如果为false，按照基本类型处理
     */
    boolean isJson() default false;

}