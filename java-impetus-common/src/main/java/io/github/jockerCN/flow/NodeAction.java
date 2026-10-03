package io.github.jockerCN.flow;

/** Operation executed by one node. A returned null is still a produced value. */
@FunctionalInterface
public interface NodeAction<C, T> {

    T execute(NodeExecution<C> execution) throws Exception;
}
