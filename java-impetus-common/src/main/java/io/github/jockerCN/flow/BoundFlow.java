package io.github.jockerCN.flow;

/** One execution binding of an existing, reusable process definition. */
public final class BoundFlow<C> {

    private final ProcessDefinition<C> definition;
    private C context;
    private boolean executed;

    BoundFlow(ProcessDefinition<C> definition, C context) {
        this.definition = definition;
        this.context = context;
    }

    public FlowRun<C> executor() {
        return definition.executor(takeContext());
    }

    public FlowRun<C> executorAsync() {
        return definition.executorAsync(takeContext());
    }

    private synchronized C takeContext() {
        if (executed) {
            throw new IllegalStateException("A bound flow can only execute once");
        }
        executed = true;
        C current = context;
        context = null;
        return current;
    }
}
