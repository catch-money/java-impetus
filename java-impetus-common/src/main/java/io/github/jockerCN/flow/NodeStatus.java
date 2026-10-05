package io.github.jockerCN.flow;

/** The lifecycle of one node in a single process execution. */
public enum NodeStatus {
    DECLARED, READY, RUNNING, WAITING, WORK_DONE,
    SUCCEEDED, STOPPED_NODE, STOPPED_FLOW, FAILED, FAILED_BY_PEER, SKIPPED;

    public boolean terminal() {
        return switch (this) {
            case SUCCEEDED, STOPPED_NODE, STOPPED_FLOW, FAILED, FAILED_BY_PEER, SKIPPED -> true;
            default -> false;
        };
    }
}
