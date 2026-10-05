package io.github.jockerCN.flow;

/** Why a node stopped waiting for another node's state. */
public enum AwaitState {
    MATCHED, TARGET_TERMINAL, STOP_REQUESTED, TIMED_OUT
}
