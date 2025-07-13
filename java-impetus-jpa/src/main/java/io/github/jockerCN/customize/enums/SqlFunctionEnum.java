package io.github.jockerCN.customize.enums;

import io.github.jockerCN.customize.annotation.AllType;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public enum SqlFunctionEnum {
    no {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
    },
    length {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    lower {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    upper {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    avg {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
    },
    ceiling {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
    },
    sum {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
    },
    max {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
    },
    min {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
    },
    count {
        @Override
        public Class<?> supportType() {
            return AllType.class;
        }
    },
    abs {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
    },
    round {
        @Override
        public Class<?> supportType() {
            return Number.class;
        }
    },
    trim {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    locate {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    coalesce {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    substring {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },
    concat {
        @Override
        public Class<?> supportType() {
            return String.class;
        }
    },

    ;
    public abstract Class<?> supportType();
}
