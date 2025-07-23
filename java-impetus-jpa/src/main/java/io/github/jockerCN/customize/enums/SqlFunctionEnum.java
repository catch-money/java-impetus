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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.ceiling(property);
        }
    },
    sqrt {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }

        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.sqrt(property);
        }

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.sqrt(property);
        }
    },
    power {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }

        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.power(property,having.power());
        }

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.power(property,(Number) args[0]);
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.count(property);
        }
    },

    countAll {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }

        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.countAll();
        }

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.countAll();
        }
    }, count1 {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }

        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.count1();
        }

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.count1();
        }
    },
    countDistinct {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }

        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.countDistinct(property);
        }

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.countDistinct(property);
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.round(property, (Integer) args[0]);
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.locate(property, (String) args[0]);
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.coalesce(property, (String) args[0]);
        }
    },
    substring {
        @Override
        public Class<?> supportType() {
            return String.class;
        }

        @Override
        public QueryExpression createQueryExpression(String property, Having having) {
            return QueryExpression.substring(property, having.substring()[0], having.substring()[1]);
        }

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.substring(property, (Integer) args[0], (Integer) args[1]);
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

        @Override
        public QueryExpression createQueryExpression(String property, Object... args) {
            return QueryExpression.concat(property, (String) args[0]);
        }
    },

    ;

    public abstract QueryExpression createQueryExpression(String property, Having having);

    public abstract QueryExpression createQueryExpression(String property, Object... args);
}
