package io.github.jockerCN.flow;

import java.time.Duration;
import java.util.function.Predicate;

/** Scope passed to a node action during one execution. */
public final class NodeExecution<C> {

    private final FlowRun<C> run;
    private final String id;
    private final Object input;

    NodeExecution(FlowRun<C> run, String id, Object input) {
        this.run = run;
        this.id = id;
        this.input = input;
    }

    @SuppressWarnings("unchecked")
    <I> I input() {
        return (I) input;
    }

    public C context() {
        return run.context();
    }

    /** Reads a peer's status, not its returned value; share data through context. */
    public NodeView state(NodeRef<C, ?> reference) {
        run.definition().requireOwned(reference);
        return run.state().view(reference.id());
    }

    public FlowView snapshot() {
        return run.snapshot();
    }

    /** Whether this running node should cooperatively stop after a flow stop or paired failure. */
    public boolean stopRequested() {
        return run.state().stopRequested(id);
    }

    public void reportProgress(int percent) {
        run.state().reportProgress(id, percent);
    }

    public void checkpoint(String name) {
        run.state().checkpoint(id, name);
    }

    public AwaitState awaitState(NodeRef<C, ?> peer, Predicate<NodeView> expected)
            throws InterruptedException {
        run.definition().requireOwned(peer);
        return run.state().awaitState(id, peer.id(), expected);
    }

    public AwaitState awaitState(NodeRef<C, ?> peer, Predicate<NodeView> expected, Duration timeout)
            throws InterruptedException {
        run.definition().requireOwned(peer);
        return run.state().awaitState(id, peer.id(), expected, timeout);
    }

    /** Publishes this paired node's own successful work before awaiting its peer. */
    public void publishSuccess() {
        run.state().publishSuccess(id);
    }

    /** Waits for the failTogether pair without polling or requiring the peer to finish first. */
    public TogetherOutcome awaitTogether() throws InterruptedException {
        return run.state().awaitTogether(id);
    }

    /** Ends this node only; dependent nodes can inspect STOPPED_NODE and continue. */
    public void stopNode() {
        throw new NodeControl(NodeControl.Kind.NODE);
    }

    /** Skips this node without producing a value; direct children are skipped as well. */
    public void skip() {
        throw new NodeControl(NodeControl.Kind.SKIP);
    }

    /** Skips this node while passing the chosen value to its direct children. */
    public <T> T skip(T value) {
        throw new NodeControl(NodeControl.Kind.SKIP, value, true);
    }

    /** Requests a cooperative stop of the whole execution. */
    public void stopFlow() {
        run.stopFlow();
        throw new NodeControl(NodeControl.Kind.FLOW);
    }
}
