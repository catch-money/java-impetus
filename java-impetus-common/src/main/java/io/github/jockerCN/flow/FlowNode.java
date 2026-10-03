package io.github.jockerCN.flow;

record FlowNode<C, T>(NodeRef<C, T> reference, NodeAction<C, T> action,
                      String parentId, Object lane, boolean async) {
}
