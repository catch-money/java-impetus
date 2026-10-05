package io.github.jockerCN.flow;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable, single-threaded builder for a process definition. */
public final class ProcessFlow<C> {

    private final Object ownerToken = new Object();
    private final Object rootLane = new Object();
    private final Map<String, FlowNode<C, ?>> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
    private final Map<String, String> togetherPeers = new LinkedHashMap<>();
    private final Map<String, NodeStateChangeListener<C>> stateListeners = new LinkedHashMap<>();
    private Duration timeout;
    private ProcessDefinition<C> definition;

    private ProcessFlow() {
    }

    /** Starts a reusable definition; supply a context separately for each execution. */
    public static <C> ProcessFlow<C> define() {
        return new ProcessFlow<>();
    }

    /** Adds an independent root node to the declaration-ordered sequential lane. */
    public <T> NodeRef<C, T> then(String id, NodeAction<C, T> action) {
        return addNode(id, action, null, rootLane, false);
    }

    /** Adds an explicit asynchronous root branch. */
    public <T> NodeRef<C, T> asyncThen(String id, NodeAction<C, T> action) {
        return addNode(id, action, null, new Object(), true);
    }

    /** Low-level alias for a sequential root node. */
    public <T> NodeRef<C, T> node(String id, NodeAction<C, T> action) {
        return then(id, action);
    }

    <I, T> NodeRef<C, T> child(NodeRef<C, I> parent, String id,
                               ChildNodeAction<C, I, T> action, boolean async) {
        requireMutable();
        requireOwned(parent);
        Objects.requireNonNull(action, "action");
        FlowNode<C, ?> parentNode = nodes.get(parent.id());
        NodeAction<C, T> adapted = execution -> action.execute(execution, execution.input());
        NodeRef<C, T> child = addNode(id, adapted, parent.id(),
                async ? new Object() : parentNode.lane(), async);
        dependencies.get(child.id()).add(parent.id());
        return child;
    }

    private <T> NodeRef<C, T> addNode(String id, NodeAction<C, T> action,
                                      String parentId, Object lane, boolean async) {
        requireMutable();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        if (id.isBlank() || nodes.containsKey(id)) {
            throw new IllegalArgumentException("Node id must be nonblank and unique: " + id);
        }
        NodeRef<C, T> reference = new NodeRef<>(id, ownerToken, this);
        nodes.put(id, new FlowNode<>(reference, action, parentId, lane, async));
        dependencies.put(id, new LinkedHashSet<>());
        return reference;
    }

    @SafeVarargs
    final void dependsOn(NodeRef<C, ?> node, NodeRef<C, ?>... predecessors) {
        requireMutable();
        requireOwned(node);
        Objects.requireNonNull(predecessors, "predecessors");
        for (NodeRef<C, ?> predecessor : predecessors) {
            requireOwned(predecessor);
            if (node == predecessor) {
                throw new IllegalArgumentException("A node cannot depend on itself: " + node.id());
            }
        }
        for (NodeRef<C, ?> predecessor : predecessors) {
            dependencies.get(node.id()).add(predecessor.id());
        }
    }

    /** Links two independent asynchronous nodes for failure propagation and joint completion. */
    public ProcessFlow<C> failTogether(NodeRef<C, ?> first, NodeRef<C, ?> second) {
        requireMutable();
        requireOwned(first);
        requireOwned(second);
        if (first == second) {
            throw new IllegalArgumentException("A node cannot fail together with itself");
        }
        if (togetherPeers.containsKey(first.id()) || togetherPeers.containsKey(second.id())) {
            throw new IllegalArgumentException("A node can belong to only one failTogether pair");
        }
        togetherPeers.put(first.id(), second.id());
        togetherPeers.put(second.id(), first.id());
        return this;
    }

    void onStateChange(NodeRef<C, ?> node, NodeStateChangeListener<C> listener) {
        requireMutable();
        requireOwned(node);
        Objects.requireNonNull(listener, "listener");
        if (stateListeners.putIfAbsent(node.id(), listener) != null) {
            throw new IllegalStateException("Node " + node.id() + " already has an onStateChange listener");
        }
    }

    /** Sets a cooperative deadline for each execution of this reusable definition. */
    public ProcessFlow<C> timeout(Duration timeout) {
        requireMutable();
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Flow timeout must be positive");
        }
        try {
            timeout.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Flow timeout is too large", overflow);
        }
        this.timeout = timeout;
        return this;
    }

    /** Validates dependencies once and freezes this builder. */
    public ProcessDefinition<C> build() {
        if (definition == null) {
            FlowGraph graph = new FlowGraph(dependencies);
            togetherPeers.forEach((id, peer) -> {
                if (!nodes.get(id).async() || !nodes.get(peer).async()
                        || graph.reaches(id, peer) || graph.reaches(peer, id)) {
                    throw new IllegalArgumentException(
                            "failTogether requires independent async nodes: " + id + ", " + peer);
                }
            });
            definition = new ProcessDefinition<>(ownerToken,
                    Collections.unmodifiableMap(new LinkedHashMap<>(nodes)), graph,
                    Collections.unmodifiableMap(new LinkedHashMap<>(togetherPeers)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(stateListeners)), timeout);
            nodes.values().forEach(node -> node.reference().seal());
        }
        return definition;
    }

    /** Freezes the nodes, then binds a context for one execution. */
    public BoundFlow<C> bind(C context) {
        return build().bind(context);
    }

    private void requireMutable() {
        if (definition != null) {
            throw new IllegalStateException("The process definition is already built");
        }
    }

    private void requireOwned(NodeRef<?, ?> reference) {
        if (Objects.isNull(reference) || reference.ownerToken() != ownerToken
                || !nodes.containsKey(reference.id())) {
            throw new IllegalArgumentException("Node reference belongs to another process definition");
        }
    }
}
