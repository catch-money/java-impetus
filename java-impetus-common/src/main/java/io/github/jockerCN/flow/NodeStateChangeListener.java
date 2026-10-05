package io.github.jockerCN.flow;

/** Observes one node's lifecycle without participating in its execution decision. */
@FunctionalInterface
public interface NodeStateChangeListener<C> {

    void onStateChange(C context, NodeStateChange change);
}
