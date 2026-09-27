package io.github.jockerCN.jpa.query.model;

import io.github.jockerCN.jpa.query.criteria.QueryExpression;
import io.github.jockerCN.jpa.query.criteria.SelectExpression;
import io.github.jockerCN.jpa.query.operator.SqlFunctionEnum;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Predicate;
import java.util.function.Function;

@Data
@EqualsAndHashCode
public class SelectColumn {

    private static final Predicate<Object> ALWAYS = ignored -> true;

    private final String name;
    private final String alias;
    private final SqlFunctionEnum function;

    private QueryExpression queryExpression;

    @Setter(AccessLevel.NONE)
    private SelectExpression selectExpression;

    private final Predicate<Object> includeWhen;

    private SelectColumn(String name, String alias, SqlFunctionEnum function,
                         QueryExpression queryExpression, Predicate<Object> includeWhen) {
        this.name = name;
        this.alias = alias;
        this.function = function;
        setQueryExpression(queryExpression);
        this.includeWhen = includeWhen;
    }

    private SelectColumn(String name, String alias, SqlFunctionEnum function,
                         SelectExpression selectExpression, Predicate<Object> includeWhen) {
        this.name = name;
        this.alias = alias;
        this.function = function;
        this.selectExpression = Objects.requireNonNull(selectExpression, "Select expression must not be null");
        this.includeWhen = includeWhen;
    }

    // 私有构造函数，只能通过Builder创建
    private SelectColumn(String name, String alias, SqlFunctionEnum function) {
        this(name, alias, function, function.createQueryExpression(name), ALWAYS);
    }

    private SelectColumn(String name, SqlFunctionEnum function) {
        this(name, name, function);
    }

    private SelectColumn(String name, String alias, SqlFunctionEnum function, Object... args) {
        // Function arguments are captured by the compiled QueryExpression.
        this(name, alias, function, function.createQueryExpression(name, args), ALWAYS);
    }


    private SelectColumn(String name, SqlFunctionEnum function, Object... args) {
        this(name, name, function, args);
    }

    /** Returns a column that is omitted when the condition rejects this query parameter. */
    public SelectColumn when(Predicate<Object> condition) {
        Predicate<Object> checkedCondition = Objects.requireNonNull(condition, "Select condition must not be null");
        return Objects.nonNull(queryExpression)
                ? new SelectColumn(name, alias, function, queryExpression, checkedCondition)
                : new SelectColumn(name, alias, function, selectExpression, checkedCondition);
    }

    /** Preserves the existing setter while keeping the SELECT operation in sync. */
    public void setQueryExpression(QueryExpression queryExpression) {
        this.queryExpression = Objects.requireNonNull(queryExpression, "Query expression must not be null");
        this.selectExpression = (criteriaBuilder, root, queryParam) -> this.queryExpression.createPredicate(criteriaBuilder, root);
    }

    public boolean includes(Object queryParam) {
        return includeWhen.test(queryParam);
    }

    /** Selects a non-null constant instead of reading an entity property. */
    public static SelectColumn constant(String alias, Object value) {
        if (StringUtils.isBlank(alias)) {
            throw new IllegalArgumentException("Select Column alias must not be empty");
        }
        Objects.requireNonNull(value, "Use nullValue(alias, type) for a null constant");
        return new SelectColumn(alias, alias, SqlFunctionEnum.no, QueryExpression.literal(value), ALWAYS);
    }

    /** Selects a typed SQL null instead of reading an entity property. */
    public static <T> SelectColumn nullValue(String alias, Class<T> type) {
        if (StringUtils.isBlank(alias)) {
            throw new IllegalArgumentException("Select Column alias must not be empty");
        }
        Objects.requireNonNull(type, "Null constant type must not be null");
        return new SelectColumn(alias, alias, SqlFunctionEnum.no, QueryExpression.nullLiteral(type), ALWAYS);
    }

    /** Builds a custom Criteria expression, including row-level CASE expressions. */
    public static SelectColumn expression(String alias, SelectExpression expression) {
        if (StringUtils.isBlank(alias)) {
            throw new IllegalArgumentException("Select Column alias must not be empty");
        }
        return new SelectColumn(alias, alias, SqlFunctionEnum.no,
                Objects.requireNonNull(expression, "Select expression must not be null"), ALWAYS);
    }

    /** Resolves a value from the current query parameter and selects it as a typed literal. */
    public static <T> SelectColumn dynamic(String alias, Class<T> type, Function<Object, ? extends T> valueResolver) {
        Class<T> javaType = Objects.requireNonNull(type, "Dynamic value type must not be null");
        Function<Object, ? extends T> resolver = Objects.requireNonNull(valueResolver, "Dynamic value resolver must not be null");
        return expression(alias, (criteriaBuilder, root, queryParam) -> {
            T value = resolver.apply(queryParam);
            return Objects.isNull(value) ? criteriaBuilder.nullLiteral(javaType) : criteriaBuilder.literal(value);
        });
    }


    public Selection<?> selection(CriteriaBuilder criteriaBuilder, Root<?> root) {
        return selection(criteriaBuilder, root, null);
    }

    public Selection<?> selection(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParam) {
        return selectExpression.create(criteriaBuilder, root, queryParam).alias(alias);
    }

    // 静态方法创建Builder
    public static Builder builder() {
        return new Builder();
    }

    // 便捷方法：创建简单列（无函数，无别名）
    public static SelectColumn of(String name) {
        return new SelectColumn(name, SqlFunctionEnum.no);
    }


    public static Set<SelectColumn> ofNames(String... names) {
        return Arrays.stream(names).map(SelectColumn::of).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // 便捷方法：创建带别名的列
    public static SelectColumn of(String name, String alias) {
        return new SelectColumn(name, alias, SqlFunctionEnum.no);
    }

    // 便捷方法：创建带函数的列
    public static SelectColumn of(String name, String alias, SqlFunctionEnum function) {
        return new SelectColumn(name, alias, function);
    }

    public static SelectColumn of(String name, String alias, SqlFunctionEnum function, Object... args) {
        return new SelectColumn(name, alias, function, args);
    }

    public static SelectColumn of(String name, SqlFunctionEnum function, Object... args) {
        return new SelectColumn(name, function, args);
    }

    public static class Builder {
        private SqlFunctionEnum function = SqlFunctionEnum.no;
        private String name;
        private String alias;
        private Object[] args;
        private Predicate<Object> includeWhen = ALWAYS;

        private Builder() {
        }

        /**
         * 设置字段名
         *
         * @param name 字段名，不能为空
         * @return Builder
         */
        public Builder name(String name) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalArgumentException("Select Column name must not be empty");
            }
            this.name = name;
            this.alias = name;
            return this;
        }

        /**
         * 设置别名
         *
         * @param alias 别名
         * @return Builder
         */
        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        /**
         * 设置SQL函数
         *
         * @param function SQL函数，不能为null
         * @return Builder
         */
        public Builder function(SqlFunctionEnum function) {
            this.function = Objects.requireNonNull(function, "Select Function must not be null");
            return this;
        }

        public Builder function(SqlFunctionEnum function, Object... args) {
            this.function = Objects.requireNonNull(function, "Select Function must not be null");
            this.args = args;
            return this;
        }

        public Builder when(Predicate<Object> condition) {
            this.includeWhen = Objects.requireNonNull(condition, "Select condition must not be null");
            return this;
        }

        /**
         * 构建SelectColumn对象
         *
         * @return SelectColumn
         * @throws IllegalStateException 如果必要字段未设置
         */
        public SelectColumn build() {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Select Column name must be set before building");
            }
            String finalAlias = StringUtils.isNotBlank(alias) ? alias : name;
            return new SelectColumn(name, finalAlias, function, args).when(includeWhen);
        }
    }

    /**
     * 集合构建器，用于构建多个SelectColumn
     */
    public static class SetBuilder {
        private final Set<SelectColumn> columns = new LinkedHashSet<>();
        private Builder currentBuilder;

        private SetBuilder() {
        }

        public static SetBuilder create() {
            return new SetBuilder();
        }

        /**
         * 开始构建新的列
         *
         * @param name 列名
         * @return SetBuilder
         */
        public SetBuilder column(final String name) {
            currentBuilder = SelectColumn.builder().name(name);
            return this;
        }

        /**
         * 为当前列设置别名
         *
         * @param alias 别名
         * @return SetBuilder
         */
        public SetBuilder alias(String alias) {
            Objects.requireNonNull(currentBuilder, "SetBuilder#alias() Must call column() first");
            currentBuilder.alias(alias);
            return this;
        }

        /**
         * 为当前列设置函数
         *
         * @param function SQL函数
         * @return SetBuilder
         */
        public SetBuilder function(SqlFunctionEnum function) {
            Objects.requireNonNull(currentBuilder, "SetBuilder#function() Must call column() first");
            currentBuilder.function(function);
            return this;
        }

        public SetBuilder function(SqlFunctionEnum function, Object... args) {
            Objects.requireNonNull(currentBuilder, "SetBuilder#function() Must call column() first");
            currentBuilder.function(function,args);
            return this;
        }

        public SetBuilder when(Predicate<Object> condition) {
            Objects.requireNonNull(currentBuilder, "SetBuilder#when() Must call column() first");
            currentBuilder.when(condition);
            return this;
        }

        /**
         * 完成当前列的构建并添加到集合
         *
         * @return SetBuilder
         */
        public SetBuilder add() {
            Objects.requireNonNull(currentBuilder, "SetBuilder#add() Must call column() first");
            columns.add(currentBuilder.build());
            currentBuilder = null;
            return this;
        }

        /**
         * 直接添加一个已构建的SelectColumn
         *
         * @param column SelectColumn对象
         * @return SetBuilder
         */
        public SetBuilder add(SelectColumn column) {
            columns.add(Objects.requireNonNull(column, "SelectColumn must not be null"));
            return this;
        }

        /**
         * 构建最终的SelectColumn集合
         *
         * @return 不可变的SelectColumn集合
         */
        public Set<SelectColumn> build() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(columns));
        }

        /**
         * 获取当前已添加的列数量
         *
         * @return 列数量
         */
        public int size() {
            return columns.size();
        }

        /**
         * 清空所有列
         *
         * @return SetBuilder
         */
        public SetBuilder clear() {
            columns.clear();
            currentBuilder = null;
            return this;
        }
    }
}
