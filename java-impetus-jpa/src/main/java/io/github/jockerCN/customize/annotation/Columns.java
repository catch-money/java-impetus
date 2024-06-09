package io.github.jockerCN.customize.annotation;

import jakarta.persistence.Tuple;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Columns {

    /**
     * 如果只是查询指定的字段有三种方法
     * 1. 在Entity对象中 为查询的字段创建构造方法
     * 2. 使用Tuple.class  (默认方式)
     * 3. 使用Object[]数组
     *
     * @return 查询类型
     */
    Class<?> value() default Tuple.class;
}
