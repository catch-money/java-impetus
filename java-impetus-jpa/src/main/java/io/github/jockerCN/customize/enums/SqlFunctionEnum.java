package io.github.jockerCN.customize.enums;

import io.github.jockerCN.customize.annotation.Having;
import io.github.jockerCN.customize.definition.AllType;
import io.github.jockerCN.customize.definition.JavaTypeSupport;
import io.github.jockerCN.customize.definition.QueryExpression;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public enum SqlFunctionEnum implements JavaTypeSupport {
    no {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.no(property);
        }
    },
    length {
        @Override
        public Class<?> supportType() {
            return String.class;
        }

        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.length(property);
        }
    },
    lower {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.lower(property);
        }
    },
    upper {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.upper(property);
        }
    },
    avg {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.avg(property);
        }
    },
    ceiling {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.ceiling(property);
        }
    },
    sum {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.sum(property);
        }
    },
    max {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.max(property);
        }
    },
    min {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.min(property);
        }
    },
    count {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.count(property);
        }
    },
    abs {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.abs(property);
        }
    },
    round {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.round(property, having.round());
        }
    },
    trim {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.trim(property);
        }
    },
    locate {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.locate(property, having.str());
        }
    },
    coalesce {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.coalesce(property, having.str());
        }
    },
    substring {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.substring(property, having.substring()[0],having.substring()[1]);
        }
    },
    concat {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.concat(property, having.str());
        }
    },

    ;
    public abstract QueryExpression createQueryExpression(String property,Having having);
}
