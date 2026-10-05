package io.github.jockerCN.flow;

import io.github.jockerCN.async.AsyncExecutorUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable flow definition that can be executed with different context instances. */
public final class ProcessDefinition<C> {

    private final Object ownerToken;
    private final Map<String, FlowNode<C, ?>> nodes;
    private final FlowGraph graph;
    private final Map<String, Object> lanes;
    private final Map<String, String> valueParents;
    private final Map<String, Integer> valueConsumers;
    private final Map<String, String> togetherPeers;
    private final Map<String, NodeStateChangeListener<C>> stateListeners;
    private final Duration timeout;

    ProcessDefinition(Object ownerToken, Map<String, FlowNode<C, ?>> nodes, FlowGraph graph,
                      Map<String, String> togetherPeers,
                      Map<String, NodeStateChangeListener<C>> stateListeners, Duration timeout) {
        this.ownerToken = ownerToken;
        this.nodes = nodes;
        this.graph = graph;
        this.togetherPeers = togetherPeers;
        this.stateListeners = stateListeners;
        this.timeout = timeout;
        Map<String, Object> compiledLanes = new LinkedHashMap<>();
        Map<String, String> compiledParents = new LinkedHashMap<>();
        Map<String, Integer> compiledConsumers = new LinkedHashMap<>();
        nodes.keySet().forEach(id -> compiledConsumers.put(id, 0));
        nodes.forEach((id, node) -> {
            compiledLanes.put(id, node.lane());
            if (node.parentId() != null) {
                compiledParents.put(id, node.parentId());
                compiledConsumers.merge(node.parentId(), 1, Integer::sum);
            }
        });
        this.lanes = Collections.unmodifiableMap(compiledLanes);
        this.valueParents = Collections.unmodifiableMap(compiledParents);
        this.valueConsumers = Collections.unmodifiableMap(compiledConsumers);
    }

    /** Runs the linear path on the calling thread; explicit parallel branches use virtual threads. */
    public FlowRun<C> executor(C context) {
        FlowRun<C> run = new FlowRun<>(this, context);
        run.execute();
        return run;
    }

    /** Binds one request's context to this reusable definition. */
    public BoundFlow<C> bind(C context) {
        return new BoundFlow<>(this, context);
    }

    /** Returns the run handle immediately and starts the whole flow on a virtual thread. */
    public FlowRun<C> executorAsync(C context) {
        FlowRun<C> run = new FlowRun<>(this, context);
        AsyncExecutorUtils.runAsync(run::execute);
        return run;
    }

    FlowNode<C, ?> node(String id) {
        return nodes.get(id);
    }

    FlowGraph graph() {
        return graph;
    }

    Map<String, Object> lanes() {
        return lanes;
    }

    Map<String, String> valueParents() {
        return valueParents;
    }

    Map<String, Integer> valueConsumers() {
        return valueConsumers;
    }

    Map<String, String> togetherPeers() {
        return togetherPeers;
    }

    Map<String, NodeStateChangeListener<C>> stateListeners() {
        return stateListeners;
    }

    Duration timeout() {
        return timeout;
    }

    void requireOwned(NodeRef<?, ?> reference) {
        if (Objects.isNull(reference) || reference.ownerToken() != ownerToken
                || !nodes.containsKey(reference.id())) {
            throw new IllegalArgumentException("Node reference belongs to another process definition");
        }
    }
}
