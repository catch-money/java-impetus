package io.github.jockerCN.customize.enums;

import io.github.jockerCN.customize.QueryPair;
import io.github.jockerCN.customize.definition.AllType;
import io.github.jockerCN.customize.definition.JavaTypeSupport;

import java.util.Collection;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public enum HavingOperatorEnum implements JavaTypeSupport {
    no{
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
    },
    isTrueOrFalse {
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
            return AllType.class;
        }
    },
    notEqual {
        @Override
        public Class<?> supportType() {
            return AllType.class;
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
    }, in {
        @Override
        public Class<?> supportType() {
            return Collection.class;
        }
    }, notIn {
        @Override
        public Class<?> supportType() {
            return Collection.class;
        }
    },
    ;


}
