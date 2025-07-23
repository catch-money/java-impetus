package io.github.jockerCN.customize;

import io.github.jockerCN.customize.definition.QueryExpression;
import io.github.jockerCN.customize.enums.SqlFunctionEnum;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode
public class SelectColumn {

    private final String name;
    private final String alias;
    private final SqlFunctionEnum function;

    private QueryExpression queryExpression;

    // 私有构造函数，只能通过Builder创建
    private SelectColumn(String name, String alias, SqlFunctionEnum function) {
        this.name = name;
        this.alias = alias;
        this.function = function;
        queryExpression = function.createQueryExpression(name);
    }

    private SelectColumn(String name, SqlFunctionEnum function) {
        this.name = name;
        this.alias = name;
        this.function = function;
        queryExpression = function.createQueryExpression(name);
    }

    private SelectColumn(String name, String alias, SqlFunctionEnum function, Object... args) {
        this.name = name;
        this.alias = alias;
        this.function = function;
        queryExpression = function.createQueryExpression(name, args);
    }


    private SelectColumn(String name, SqlFunctionEnum function, Object... args) {
        this.name = name;
        this.alias = name;
        this.function = function;
        queryExpression = function.createQueryExpression(name, args);
    }


    public Selection<?> selection(CriteriaBuilder criteriaBuilder, Root<?> root) {
        return queryExpression.createPredicate(criteriaBuilder, root).alias(alias);
    }

    // 静态方法创建Builder
    public static Builder builder() {
        return new Builder();
    }

    // 便捷方法：创建简单列（无函数，无别名）
    public static SelectColumn of(String name) {
        return new SelectColumn(name, SqlFunctionEnum.no);
    }


    public static Set<SelectColumn> of(String... names) {
        return Arrays.stream(names).map(SelectColumn::of).collect(Collectors.toSet());
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
            return new SelectColumn(name, finalAlias, function, args);
        }
    }

    /**
     * 集合构建器，用于构建多个SelectColumn
     */
    public static class SetBuilder {
        private final Set<SelectColumn> columns = new HashSet<>();
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
            return Set.copyOf(columns);
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