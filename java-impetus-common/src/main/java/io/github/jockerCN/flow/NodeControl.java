package io.github.jockerCN.flow;

/** Internal stackless signal used only to leave the current user action immediately. */
final class NodeControl extends RuntimeException {

    enum Kind { NODE, FLOW, SKIP }

    private final Kind kind;
    private final Object value;
    private final boolean hasValue;

    NodeControl(Kind kind) {
        this(kind, null, false);
    }

    NodeControl(Kind kind, Object value, boolean hasValue) {
        super(null, null, false, false);
        this.kind = kind;
        this.value = value;
        this.hasValue = hasValue;
    }

    Kind kind() {
        return kind;
    }

    Object value() {
        return value;
    }

    boolean hasValue() {
        return hasValue;
    }
}
