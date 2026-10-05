package io.github.jockerCN.flow;

/** Joint outcome of a failTogether pair, separate from each node's own work. */
public enum TogetherStatus {
    SUCCEEDED, FAILED, STOPPED, TIMED_OUT
}
