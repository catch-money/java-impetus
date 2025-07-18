package io.github.jockerCN.customize.enums;

import io.github.jockerCN.customize.QueryPair;

import io.github.jockerCN.customize.definition.JavaTypeSupport;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public enum HavingOperatorEnum implements JavaTypeSupport {
    no{
        @Override
        public Class<?> supportType() {
            return Object.class;
        }
    },
    isTrue {
        @Override
        public Class<?> supportType() {
            return Boolean.class;
        }
    },
    isFalse {
        @Override
        public Class<?> supportType() {
            return Boolean.class;
        }
    },
    isNull {
        @Override
        public Class<?> supportType() {
            return Boolean.class;
        }
    },
    isNotNull {
        @Override
        public Class<?> supportType() {
            return Boolean.class;
        }
    },
    equal {
        @Override
        public Class<?> supportType() {
            return Object.class;
        }
    },
    notEqual {
        @Override
        public Class<?> supportType() {
            return Object.class;
        }
    },
    between {
        @Override
        public Class<?> supportType() {
            return QueryPair.class;
        }
    },
    gt {
        @Override
        public Class<?> supportType() {
            return Comparable.class;
        }
    },
    ge {
        @Override
        public Class<?> supportType() {
            return Comparable.class;
        }
    },
    lt {
        @Override
        public Class<?> supportType() {
            return Comparable.class;
        }
    },
    le {
        @Override
        public Class<?> supportType() {
            return Comparable.class;
        }
    },
    like {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    notLike {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    ;


}
