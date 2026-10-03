package io.github.jockerCN.flow;

import io.github.jockerCN.async.AsyncExecutorUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

/** One execution with its original context, node outcomes and completion signal. */
public final class FlowRun<C> {

    private final ProcessDefinition<C> definition;
    private final C context;
    private final FlowExecutionState state;
    private final CompletableFuture<FlowRun<C>> completion = new CompletableFuture<>();
    private final NodeStateChangeDispatcher<C> observer;
    private final ScheduledFuture<?> deadline;

    FlowRun(ProcessDefinition<C> definition, C context) {
        this.definition = definition;
        this.context = context;
        observer = definition.stateListeners().isEmpty() ? null
                : new NodeStateChangeDispatcher<>(definition.stateListeners(), context);
        state = new FlowExecutionState(definition.graph(), definition.lanes(),
                definition.valueParents(), definition.valueConsumers(), definition.togetherPeers(),
                definition.stateListeners().keySet(), observer == null ? null : observer::publish);
        deadline = definition.timeout() == null || state.isComplete() ? null
                : FlowDeadlineScheduler.schedule(this::timeoutFlow, definition.timeout());
        if (deadline != null) {
            completion.whenComplete((result, failure) -> deadline.cancel(false));
        }
    }

    public C context() {
        return context;
    }

    public FlowView snapshot() {
        return state.snapshot();
    }

    /** Completes when all nodes are terminal, independently of observer callbacks. */
    public CompletableFuture<FlowRun<C>> completion() {
        return completion;
    }

    /** Completes after the final pending state change callback has returned. */
    public CompletableFuture<Void> observationCompletion() {
        return observer == null ? CompletableFuture.completedFuture(null) : observer.completion();
    }

    public NodeOutcome outcome(NodeRef<C, ?> reference) {
        definition.requireOwned(reference);
        return state.outcome(reference.id());
    }

    /** Requests cooperative termination of this run without interrupting active tasks. */
    public void stopFlow() {
        state.stopFlow();
        completeIfFinished();
    }

    private void timeoutFlow() {
        state.timeoutFlow();
        completeIfFinished();
    }

    void execute() {
        try {
            List<String> initial = state.claimReady();
            String inline = dispatch(initial);
            if (inline != null) {
                runChain(inline);
            }
            completeIfFinished();
            completion.join();
        } catch (Throwable failure) {
            abort(failure);
            throw failure;
        }
    }

    /** Only async nodes fork; nodes in the same sequential lane are claimed one at a time. */
    private String dispatch(List<String> claimed) {
        if (claimed.isEmpty()) {
            return null;
        }
        String inline = null;
        for (String id : claimed) {
            if (!definition.node(id).async()) {
                inline = id;
                break;
            }
        }
        if (inline == null && Thread.currentThread().isVirtual()) {
            inline = claimed.getFirst();
        }
        for (String id : claimed) {
            if (!id.equals(inline)) {
                AsyncExecutorUtils.executor(() -> runChain(id));
            }
        }
        return inline;
    }

    private void runChain(String firstId) {
        try {
            String current = firstId;
            while (current != null) {
                executeNode(current);
                current = dispatch(state.claimReady());
            }
            completeIfFinished();
        } catch (Throwable failure) {
            abort(failure);
        }
    }

    private void executeNode(String id) {
        if (state.stopRequested(id)) {
            state.stopRequestedNode(id);
            return;
        }
        FlowNode<C, ?> node = definition.node(id);
        try {
            Object input = null;
            if (node.parentId() != null) {
                input = state.parentInput(id);
                if (input == FlowExecutionState.NO_PARENT_VALUE) {
                    state.skipRunning(id);
                    return;
                }
            }
            Object result = node.action().execute(new NodeExecution<>(this, id, input));
            state.succeed(id, result);
        } catch (NodeControl control) {
            if (control.kind() == NodeControl.Kind.FLOW) {
                state.stopFlowNode(id);
            } else if (control.kind() == NodeControl.Kind.SKIP) {
                state.skipRunningExplicit(id, control.hasValue(), control.value());
            } else {
                state.stopNode(id);
            }
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            state.fail(id, failure);
        }
    }

    private void completeIfFinished() {
        if (state.isComplete()) {
            if (observer != null) {
                observer.close();
            }
            completion.complete(this);
        }
    }

    private void abort(Throwable failure) {
        state.stopFlow();
        if (observer != null) {
            observer.abort();
        }
        completion.completeExceptionally(failure);
    }

    int pendingObservationCount() {
        return observer == null ? 0 : observer.pendingCount();
    }

    ProcessDefinition<C> definition() {
        return definition;
    }

    FlowExecutionState state() {
        return state;
    }
}
