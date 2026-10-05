package io.github.jockerCN.jpa.annotation;

import java.lang.annotation.*;

/**
 * 标记由调用方提供的 {@code Collection<SelectColumn>} 动态选列字段。
 * 结果类型由查询入口的 findType 指定，未指定时使用实体类型。
 *
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Columns {
}
