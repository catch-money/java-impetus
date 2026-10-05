package io.github.jockerCN.flow;

import java.util.Objects;

/** Typed identity of a node in one process definition. */
public final class NodeRef<C, T> {

    private final String id;
    private final Object ownerToken;
    private ProcessFlow<C> builder;

    NodeRef(String id, Object ownerToken, ProcessFlow<C> builder) {
        this.id = id;
        this.ownerToken = ownerToken;
        this.builder = builder;
    }

    public String id() {
        return id;
    }

    /** This node becomes ready after all named predecessors reach a terminal state. */
    @SafeVarargs
    public final NodeRef<C, T> dependsOn(NodeRef<C, ?>... predecessors) {
        ProcessFlow<C> owner = builder;
        if (Objects.isNull(owner)) {
            throw new IllegalStateException("The process definition is already built");
        }
        owner.dependsOn(this, predecessors);
        return this;
    }

    /** Registers this node's sole lifecycle observer before the definition is built. */
    public NodeRef<C, T> onStateChange(NodeStateChangeListener<C> listener) {
        owner().onStateChange(this, listener);
        return this;
    }

    /** Adds a sequential child that receives this node's successful result. */
    public <R> NodeRef<C, R> then(String id, ChildNodeAction<C, T, R> action) {
        return owner().child(this, id, action, false);
    }

    /** Adds a parallel child that receives this node's successful result. */
    public <R> NodeRef<C, R> asyncThen(String id, ChildNodeAction<C, T, R> action) {
        return owner().child(this, id, action, true);
    }

    private ProcessFlow<C> owner() {
        if (Objects.isNull(builder)) {
            throw new IllegalStateException("The process definition is already built");
        }
        return builder;
    }

    Object ownerToken() {
        return ownerToken;
    }

    void seal() {
        builder = null;
    }
}
