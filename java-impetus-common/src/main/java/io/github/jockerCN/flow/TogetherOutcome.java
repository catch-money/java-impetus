package io.github.jockerCN.flow;

/** Joint decision: failure or stop resolves immediately; success requires both local reports. */
public record TogetherOutcome(TogetherStatus status, String sourceNodeId, Throwable failure) {

    public boolean succeeded() {
        return status == TogetherStatus.SUCCEEDED;
    }

    public boolean failed() {
        return status == TogetherStatus.FAILED;
    }
}
