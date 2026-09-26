package io.github.jockerCN.jpa.query.value;

/** Adjusts the caller's query parameter before its annotated fields are read. */
@FunctionalInterface
public interface QueryParamProcessor {
    void process(Object queryParam);

    final class None implements QueryParamProcessor {
        @Override
        public void process(Object queryParam) {
        }
    }
}
