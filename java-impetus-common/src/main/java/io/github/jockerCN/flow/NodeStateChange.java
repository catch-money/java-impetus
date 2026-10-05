package io.github.jockerCN.flow;

/** One immutable lifecycle update; intermediate updates may be coalesced before delivery. */
public record NodeStateChange(String nodeId, NodeStatus previousStatus, NodeView current) {
}
