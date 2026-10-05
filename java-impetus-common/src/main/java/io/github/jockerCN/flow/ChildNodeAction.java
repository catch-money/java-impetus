package io.github.jockerCN.flow;

/** A child operation receives the successful return value of its direct parent. */
@FunctionalInterface
public interface ChildNodeAction<C, I, T> {

    T execute(NodeExecution<C> execution, I input) throws Exception;
}
