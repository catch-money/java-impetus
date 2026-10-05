package io.github.jockerCN.flow;

import java.util.concurrent.locks.Condition;

/** Shared, per-run completion barrier for one failTogether pair. */
final class FlowTogetherGroup {

    final FlowRuntimeNode first;
    final FlowRuntimeNode second;
    final Condition changed;
    TogetherOutcome outcome;
    boolean dependentsReleased;

    FlowTogetherGroup(FlowRuntimeNode first, FlowRuntimeNode second, Condition changed) {
        this.first = first;
        this.second = second;
        this.changed = changed;
    }

    FlowRuntimeNode peer(FlowRuntimeNode member) {
        return member == first ? second : first;
    }
}
