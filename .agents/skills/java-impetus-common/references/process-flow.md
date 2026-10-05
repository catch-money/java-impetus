# ProcessFlow usage (2.0.0)

Package: `io.github.jockerCN.flow`. Build a reusable definition once, then bind a fresh application context for each execution. Do not build the node graph for every request unless its structure genuinely varies.

```java
ProcessFlow<OrderContext> flow = ProcessFlow.define();
NodeRef<OrderContext, Order> load = flow.then("load", step -> loadOrder(step.context()));
NodeRef<OrderContext, Receipt> save = load.then("save", (step, order) -> {
    Receipt receipt = saveOrder(order);
    step.context().setReceipt(receipt); // Persist the business result in caller-owned context.
    return receipt;
});
ProcessDefinition<OrderContext> definition = flow.build();

OrderContext context = new OrderContext();
FlowRun<OrderContext> run = definition.executor(context);
NodeOutcome outcome = run.outcome(save); // Status and failure, not the returned Receipt.
Receipt receipt = context.getReceipt();
```

`then` creates a sequential node. Several independent root `then` nodes run in declaration order but **do not exchange return values**. Use `parent.then(...)` for a typed child that receives the parent's return value. Use `asyncThen` to explicitly fork a virtual-thread branch. A `NodeRef.dependsOn(a, b)` waits for those nodes to reach terminal states; it does not inject their returned values. For independent-node data exchange, store the needed value in the shared context. Concurrent context mutation is the caller's responsibility.

`ProcessDefinition` is reusable. `definition.bind(context).executor()` and `definition.executor(context)` run one flow; `executorAsync(context)` returns the run handle immediately and starts the whole execution on a virtual thread. A synchronous linear run stays on the calling thread; explicit async nodes fork. Every execution gets a new `FlowRun` and state. The definition holds callbacks and actions, so mutable state captured in those lambdas must be safe across runs.

## Values and control

Within a node, use ordinary Java `if/else`; the API has no `when`/`otherwise` chain. `step.reportProgress(40)` replaces, rather than increments, that node's progress. `step.checkpoint(name)` records a stage marker.

- `return value` passes a value only to direct child nodes. That runtime reference is released after its consumers take it; `run.outcome(ref)` exposes only status and exception.
- `return step.skip(value)` marks the node `SKIPPED` and passes the explicit value to direct children. `step.skip()` skips without a value, so direct children are skipped too.
- `step.stopNode()` stops this node without a value. `step.stopFlow()` and `run.stopFlow()` request a cooperative stop of the whole run; active business code should check `step.stopRequested()` and exit. No action forcibly rolls back side effects.

After completion, `run.snapshot()` gives the final status/progress/timing view. The flow clears transient scheduling data and node return values; the original context and final snapshot remain available. Save any final business result in that context before the node ends.

## Observation, waiting, and parallel failure

Register at most one `nodeRef.onStateChange((context, change) -> ...)` before `build()`. It observes that node's state asynchronously; it is not a business rollback hook. Wait for `run.completion()` to know node execution has ended; then wait for `run.observationCompletion()` if the caller must also observe every pending state callback. Callback exceptions are logged and do not change node status. Since the same definition may run concurrently, callbacks must handle shared captured state safely.

`step.state(peer)`/`step.snapshot()` inspect status; `step.awaitState(peer, predicate)` waits for a state, and its overload accepts a `Duration` timeout. These APIs do not expose the peer's return value. Avoid unbounded mutually waiting predicates: a user's business-level wait cycle cannot be made productive by the scheduler.

Two independent `asyncThen` nodes may be paired with `flow.failTogether(a, b)`. Either failure signals the other to stop cooperatively, and their successors wait until both finish. A node that needs to compensate its own successful work may call `step.publishSuccess()` and then `step.awaitTogether()`; inspect the returned `TogetherOutcome` and perform its own rollback if required. This is not a transaction manager or automatic business rollback.

`flow.timeout(Duration.ofSeconds(30))` sets a per-run cooperative deadline. It prevents new nodes from starting and signals running nodes, but `completion()` still waits for user code that ignores the signal. Use `step.stopRequested()` around long-running or blocking work.
