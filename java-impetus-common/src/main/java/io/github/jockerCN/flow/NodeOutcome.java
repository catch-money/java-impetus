package io.github.jockerCN.flow;

/** Terminal status; node return values are released when the flow finishes. */
public record NodeOutcome(NodeStatus status, Throwable failure) {
}
