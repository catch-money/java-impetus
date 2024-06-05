package io.github.jockerCN.jpa.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Getter
@Setter
public class QueryParams<T> {


    private Class<T> t;

    private int limitCount;

    private final Map<String, Collection<?>> in = new HashMap<>();

    private final Map<String, Collection<?>> notIn = new HashMap<>();

    private final Map<String, Object> whereEq = new HashMap<>();

    private final Set<String> groupBy = new HashSet<>();

    private final Set<ComparatorCondition> comp = new HashSet<>();

    private final Map<String, String> like = new HashMap<>();

    private final Map<String, String> notLike =  new HashMap<>();

    private final Map<String, TimeComparator> timeComparator =  new HashMap<>();

    private final Set<OderByCondition> OrderBy = new HashSet<>();

    private final Set<String> queryColumns = new HashSet<>();


    public boolean enableQueryColumns() {
        return !queryColumns.isEmpty();
    }

    public QueryParams<T> of(Class<T> clazz) {
        this.t = clazz;
        return this;
    }


    public QueryParams<T> queryColumns(String... columnName) {
        queryColumns.addAll(Arrays.asList(columnName));
        return this;
    }

    public QueryParams<T> queryColumns(Set<String> columnName) {
        queryColumns.addAll(columnName);
        return this;
    }

    public QueryParams<T> like(String columnName, String columnValue) {
        like.put(columnName, columnValue);
        return this;
    }

    public QueryParams<T> notLike(String columnName, String columnValue) {
        notLike.put(columnName, columnValue);
        return this;
    }


    public QueryParams<T> eq(String filedName, Object value) {
        whereEq.put(filedName, value);
        return this;
    }

    public QueryParams<T> in(String filedName, Collection<?> value) {
        in.put(filedName, value);
        return this;
    }

    public QueryParams<T> notIn(String filedName, Collection<?> value) {
        notIn.put(filedName, value);
        return this;
    }

    public QueryParams<T> groupBy(String filedName) {
        groupBy.add(filedName);
        return this;
    }

    public QueryParams<T> groupBy(Set<String> filedName) {
        groupBy.addAll(filedName);
        return this;
    }


    public QueryParams<T> ge(String fieldName, Object value) {
        comp.add(new ComparatorCondition(fieldName, value, SQLCondition.GE));
        return this;
    }

    public QueryParams<T> geAndEq(String fieldName, Object value) {
        comp.add(new ComparatorCondition(fieldName, value, SQLCondition.GE_EQ));
        return this;
    }

    public QueryParams<T> le(String fieldName, Object value) {
        comp.add(new ComparatorCondition(fieldName, value, SQLCondition.LE));
        return this;
    }

    public QueryParams<T> leAndEq(String fieldName, Object value) {
        comp.add(new ComparatorCondition(fieldName, value, SQLCondition.LE_EQ));
        return this;
    }

    public QueryParams<T> betweenAnd(String fieldName, LocalDateTime startTime, LocalDateTime endTime) {
        timeComparator.put(fieldName, new TimeComparator(startTime, endTime));
        return this;
    }

    public QueryParams<T> limit(int limit) {
        this.limitCount = limit;
        return this;
    }

    public QueryParams<T> orderBy(String fieldName, OderBy oderBy) {
        OrderBy.add(new OderByCondition(fieldName, oderBy));
        return this;
    }

    @Getter
    @AllArgsConstructor
    public static class OderByCondition {
        private String filedName;
        private OderBy oderBy;
    }

    public enum OderBy {
        ASC,
        DESC
    }

    @Getter
    @AllArgsConstructor
    public static class TimeComparator {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Getter
    @AllArgsConstructor
    public static class ComparatorCondition {

        private String filedName;

        private Object value;

        private SQLCondition sqlCondition;

    }

    public enum SQLCondition {
        LIKE,
        GE,
        LE,
        GE_EQ,
        LE_EQ,
    }

}
