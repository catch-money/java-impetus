# ProcessFlow 接口草图与状态约定

本文件记录流程引擎的已实现范围与后续设计。当前已落地根层及带类型子节点的 `then/asyncThen`、直接父子返回值传递、`dependsOn`、`failTogether` 双节点联动／完成屏障、定义级协作式超时、同步／异步执行、运行快照、节点内控制 API，以及单节点 `onStateChange` 观察回调。业务条件由调用方写在节点函数中；无仓库内调用的旧 `fluent` 包已移除。

## 1. 两层对象

- `ProcessFlow<C>`：流程定义的入口／构建器。`define()` 构建节点，再由 `build()` 得到可复用的不可变 `ProcessDefinition<C>`。构建器上的 `bind(context)` 会先固化定义；不再通过静态 `ProcessFlow.bind(context)` 为每次请求重新声明节点。
- `BoundFlow<C>`：`definition.bind(context)` 为一次请求绑定调用方创建的原 context；同一个绑定只允许执行一次，但同一份定义可以反复绑定不同 context。
- `ProcessDefinition<C>`：只保存节点函数、编译好的依赖图、互不重叠的 `failTogether` 配对、每节点至多一个状态观察回调和可选期限，不保存某次运行的 context、阶段值、异常或进度。
- `FlowRun<C>`：一次 `executor()` 对应一次执行，持有调用方传入的**同一个** context 和本次运行的节点状态。无直接子节点的返回值立即释放；有子节点时，最后一个子节点领取后释放父节点的引用。全部节点终态后固化最终快照并清理可变运行状态；不同执行不共享状态，不创建静态运行注册表。
- `NodeRef<C, T>`：带 context 类型与返回值类型的节点标识。根层通过 `ProcessFlow.then/asyncThen` 声明；子层通过 `parent.then/asyncThen` 声明，子节点接收直接父节点正常返回或 `skip(value)` 显式传递的值。`node(id, action)` 是根层顺序 `then` 的低层别名；`dependsOn(...)` 增加终态等待关系。跨流程引用、重名、自依赖、显式依赖环在编译阶段拒绝。
- `NodeExecution<C>`：当前节点的执行作用域，提供原 context、其他节点的状态视图、进度、检查点、等待和停止能力；`stopRequested()` 供长任务协作响应全局停止或配对失败。配对节点还可 `publishSuccess()` 和 `awaitTogether()`，不向独立节点注入彼此的返回值。
- `NodeRef.onStateChange(listener)`：只为当前 `then/asyncThen` 产生的节点登记一个观察者；重复登记和定义固化后的登记均拒绝。回调以原 context、前一状态及当前不可变 `NodeView` 为入参，不代替节点内的业务补偿。
- `NodeOutcome`：`FlowRun.outcome(ref)` 只提供终态状态和异常，不保留业务返回值。`NodeView.hasValue` 表示节点曾产出正常返回值或 `skip(value)` 指定的值（包括 `null`）。需要在流程结束后读取的业务结果，由节点写入原 context。

内部的 `FlowExecutionState` 保留一次运行的状态转换和唯一短锁；`FlowRuntimeNode` 保存单节点状态与视图生成，`FlowLaneScheduler` 管理就绪 lane，`FlowTogetherGroup` 保存配对完成屏障。它们不进入公开 API，也不额外复制业务 context 或节点返回值。

```java
ProcessFlow<MyContext> builder = ProcessFlow.define();
NodeRef<MyContext, List<Video>> search = builder.then("search", step -> {
    List<Video> videos = search(step.context());
    return videos;
});
NodeRef<MyContext, Void> audit = builder.asyncThen("audit", step -> { audit(step.context()); return null; });
// audit 显式异步，因此与 search 并行。
NodeRef<MyContext, Video> select = search.then("select", (step, videos) -> {
    Video selected = select(videos);
    step.context().setSelected(selected);
    return selected;
}).dependsOn(audit);
ProcessDefinition<MyContext> definition = builder.build();

FlowRun<MyContext> run = definition.bind(context).executor(); // 调用线程同步执行
FlowView view = run.snapshot();

// 下次请求复用同一份 definition：definition.bind(nextContext).executor()
// 也可省略绑定对象，直接调用 definition.executor(context)
// 如需整个流程异步执行：
// FlowRun<MyContext> asyncRun = definition.bind(context).executorAsync(); // 立即拿到运行句柄
// asyncRun.snapshot();                                       // 运行中可查看状态
// asyncRun.completion().join();                              // 需要时等待最终结果
```

根层多个独立 `then` 按声明顺序执行；它们的入参相互独立，不自动传递返回值。若较早的 `search2` 显式依赖后面声明的 `search3`，调度器会先运行可执行的 `search3`，再回到 `search2`，不会把默认顺序编译为造成环的硬依赖。`NodeRef.dependsOn(...)` 只等待前驱进入终态，不要求成功，也不注入值；独立节点间值交互走 context。带类型子节点只接收直接父节点正常返回或 `skip(value)` 传出的值，包括 `null`；父节点失败、停止或无值跳过时，子节点及其连续子链标记 `SKIPPED`。

`when/Decision/otherwise` 及内部条件节点已移除。普通 `if/else` 与业务进度都由调用方在 `then` 内处理。`step.skip()` 立即结束当前节点、不产出值，直接子节点也会跳过；`step.skip(value)` 立即结束并把调用方选定的对象作为当前节点的值，直接子节点可以继续执行，当前节点最终状态仍为 `SKIPPED`。该值可以是 `null`，与无参跳过严格区分。`step.stopNode()` 只中止当前节点且不产出值；`step.stopFlow()` 请求停止整个运行。三种控制都将最终状态记录在当前节点。`dependsOn` 仍只用于等待终态和读取状态／异常，不保存依赖节点的业务返回值。

## 2. 顺序、并行与同伴观察

| 关系 | 语义 | 是否允许环 |
| --- | --- | --- |
| `node2.dependsOn(node1)` | `node1` **进入终态**后 `node2` 才可调度；依赖方读状态／失败信息，值交互走 context。`requiresSuccess(node1)` 可作为以后增加的便捷策略。 | 不允许 |
| 同一根层的多个 `then` | 可以同时具备依赖就绪条件，但调度时共用一个顺序 lane；按声明顺序逐个执行，遇到被显式依赖挡住的节点则先取下一可执行节点。 | 不适用 |
| 同一根层的多个 `asyncThen` | 显式分到不同 lane，可通过 `AsyncExecutorUtils.runAsync` 并行；不承诺同一个时钟刻度启动。 | 不适用 |
| `observe(peer)` / `awaitState(peer, ...)` | 运行中查看或等待同伴状态，不改变调度依赖。两个并行节点可互相观察。 | 可互相观察，但等待条件必须可达 |
| `failTogether(node1, node2)` | 限两个互不依赖的显式异步节点，定义期注册且不可重叠。一方失败即通知同伴；本地成功可提前公布为 `WORK_DONE`，但双方的后继要等两个节点都终结。 | 不引入调度环 |

失败传播是**协作式**的：已经运行的节点不会被强行中断，可在关键点查看 `state(peer).stopRequested()` 或调用可被唤醒的 `awaitTogether`／`awaitState`；外部副作用由用户自行回滚。`publishSuccess()` 是本地工作成功上报，快照显示 `WORK_DONE` 与 `localSucceeded=true`；双方都上报后 `awaitTogether()` 返回组合成功，一方失败则立即返回 `FAILED`，即使失败发生在等待之前也不会漏掉。失败后的补偿代码须仍在节点函数中执行；一旦函数已返回，框架不会自动再次调用它。`skip`／`stopNode` 令组合得到 `STOPPED`，不伪装成业务异常。全局 `stopFlow()` 阻止未开始节点启动，运行中节点收到停止信号，完成时最终标记 `STOPPED_FLOW`。组合的首个真实失败保留，后续回滚异常记录在回滚节点自身。

## 3. 等待与视图

```java
// 当前已实现的执行作用域 API：节点自行选择是否设置超时。
AwaitState awaitState(NodeRef<C, ?> peer, Predicate<NodeView> expected);
AwaitState awaitState(NodeRef<C, ?> peer, Predicate<NodeView> expected, Duration timeout);
void publishSuccess(); // 仅 failTogether 成员，先上报本地工作成功
TogetherOutcome awaitTogether(); // 先检查已有组合结果，再等待变更
NodeView state(NodeRef<C, ?> peer);
void checkpoint(String name);
FlowView snapshot();
void skip();
<T> T skip(T value);
void stopNode();
void stopFlow();
```

`AwaitState` 区分 `MATCHED`、`TARGET_TERMINAL`（目标已经结束但条件未满足）、`STOP_REQUESTED`、`TIMED_OUT`；不把超时或目标失败当成成功。无超时版本可一直等待，但同伴终态和全局停止必须唤醒它。两个节点在进入等待前各自发布命名 `checkpoint`，就能互相等待对方到达该检查点；若两者都等待对方一个尚未发布且再也无法发布的状态，属于定义错误，**无超时等待可能永久阻塞**，因此跨节点等待通常应设置超时。后续可检查明显不可达的等待条件。

当前测试模型的 `Predicate<NodeView>` 在状态锁外判定，并用目标节点的版本号在重新入锁后复查，避免用户条件函数阻塞状态更新。公开 API 仍可进一步收窄为状态集合／命名检查点，以减少误用和谓词开销。

`FlowView` 是每次运行的结构性不可变快照，包含所有节点状态、同时运行的节点集合、等待中的节点集合（包括 `WORK_DONE` 且正在 `awaitTogether()` 的节点）、终态数／总节点数、全局停止请求及是否超时，不包含节点返回值。配对节点本地成功与等待组合分别由 `NodeView.localSucceeded()` 和 `awaitingTogether()` 表达。`runningNodes`／`waitingNodes` 保留定义顺序，checkpoint 保留上报顺序。进度为 `终态数 / 总节点数`，仅代表流程节点完成比例，不代表耗时或业务百分比。全部节点终态时先固化一次最终快照，之后的 `snapshot()` 返回同一对象；快照中的 checkpoint 是历史副本，内部可变集合则已清理。业务 context 和最终结果对象不做深拷贝；并行节点若修改同一个 context，字段同步和数据竞争由用户自己约束，流程引擎不复制或串行化 context。

每个节点还可调用 `reportProgress(int percent)` 主动**覆盖**自己的进度，范围为 0–100；它不累加，也不参与整体的“终态节点占比”。成功终态自动为 100%，失败／停止则保留最后一次上报值。节点视图提供开始时间、结束时间和耗时；流程视图提供流程开始时间、全部节点终态时的结束时间和总耗时。耗时用单调时钟计算，时间戳用于展示；等待同伴的时间包含在节点墙钟耗时中，未启动的节点耗时为零。

状态变化与等待注册使用同一同步机制，避免“先检查、后订阅”漏信号。`onStateChange` 在定义期登记；每个被观察节点首次产生事件时，经 `AsyncExecutorUtils` 按需启动一条虚拟线程，在状态锁外处理该节点回调。节点之间的回调互不等待，同一节点的回调保持顺序，不为每个事件分别建线程。每个观察节点最多保留一条待处理更新，慢回调时同节点中间状态会合并成最新状态，最终终态仍会送达，因此它是状态观察而非完整事件日志。事件覆盖状态切换、局部成功上报、组合等待标记和协作停止信号，不因进度或 checkpoint 的单独变化触发。观察者异常只记录日志，不改变节点结果；`completion()` 只等待节点终态，`observationCompletion()` 单独等待回调处理完毕。回调函数属于可复用定义，同一定义的多次执行可能并发调用它，共享状态的线程安全由调用方负责。

## 4. 执行与性能边界

- `executor(context)` 是**同步入口**。若全程没有显式并行，节点就在调用线程顺序跑到底，不额外创建虚拟线程。用户选 `executorAsync(context)` 时，立即返回可观察的 `FlowRun`，**整个流程**先提交到一条虚拟线程；线性节点继续在这条虚拟线程中执行。运行句柄另提供完成信号，不能等任务结束才返回句柄。
- 只有 `asyncThen` 显式创建的分支才通过 `AsyncExecutorUtils` 提交虚拟线程。同步调用可继续运行根层顺序 `then`，同时执行异步分支；如果只有异步分支，调用线程等待。异步入口本已在虚拟线程上，可让当前虚拟线程继续其中一个分支。分支内部的线性节点沿用所在虚拟线程，不按“每节点一个任务”反复调度。汇合后的顺序段由最后完成依赖的线程接续。
- 状态更新只持有**一次运行范围**内的短锁；调用用户节点函数、状态回调和阻塞等待时不能持锁。依赖完成只检查直接后继，不能每个节点结束都全图扫描。正常状态变化只唤醒观察该节点的等待者，全局停止才批量唤醒。完整快照按需构建，不在每次状态变化时构建。`StreamUtils` 若有同语义的集合转换可复用，但不会为替代短路径直接操作而强行生成 Stream。
- 依赖图、反向边与环检测在 `ProcessDefinition` 编译时做**一次**；每次运行只创建状态，不重复编译规则。状态模型保留接收原始 `Map` 的包内测试构造入口。
- `ProcessDefinition` 只保存节点函数、依赖图、执行 lane 和直接子节点的取值计数，不保存任何请求的 context 或返回值。节点正常返回或 `skip(value)` 指定的对象只存于本次运行状态中：没有直接子节点的值立即释放，多个子节点则在最后一个领取后释放；纯 `dependsOn` 不延长值的生命周期。`skip(value)` 不复制对象，只把同一引用作为当前节点的输出；无参 `skip()`、停止和失败路径不保存返回值。全部节点终态时先固化 `FlowView`，再清空可变节点状态、checkpoint、待调度队列和依赖计数；完成信号随后发布。原 context 属于调用方，`FlowRun.context()` 仍可访问；一次性 `BoundFlow` 提交后释放自己对 context 的引用。外部缓存和节点主动写入 context 的对象不归引擎清理。
- `failTogether` 的内部失败联动在状态转换时直接送达，不等待 `onStateChange` 观察者；观察者回调在锁外执行并隔离异常，不能覆盖原节点失败。
- 定义级 `timeout(Duration)` 对每次运行单独计时；共用一个计时器，不为每个节点建定时任务，完成后取消待触发的期限。计时器到点后把停止处理提交到虚拟线程，避免一次大流程的停止扫描拖延其他流程的到期信号；提交失败时在计时器线程内兜底执行。超时调用协作式全局停止，`FlowView.timedOut()` 留下原因，配对等待返回 `TIMED_OUT`。正在运行且不响应停止的用户代码不会被强杀，`completion()` 仍等待其退出。
- 最小调度器已有线性链线程保持、显式并行分叉／汇合、根层软顺序与 2,000 节点链的功能测试。并发基线覆盖同一份定义同时执行 65 次、双向检查点等待、失败／局部停止后的汇合、24 子节点扇出、最后一个子节点领取后提前释放父值、未启动子节点因全局停止释放父值、停止某次运行而另一运行正常完成、最终快照隔离与可变状态清空。容量回归再覆盖一份定义同时运行 8 次、每次 96 个异步子节点及同数状态观察者，连续重复 3 次；在测试 JVM 指定 `-Xmx128m` 时也通过，并验证父值提前释放、全部状态回调完成及最终运行时数据清空。这是功能与清理基线，不把测试耗时当成跨环境性能承诺；吞吐、分配量与依赖就绪延迟若要定量比较，应另做同机基准测试。

本轮结构回归：普通并行分支只提交任务，不为未使用的 `CompletableFuture` 分配对象；观察回调按节点复用一条按需启动的虚拟线程，同节点只缓存一条待派发状态，不同节点互不阻塞。每次运行只在使用 `awaitState` 或 checkpoint 时为对应节点创建等待条件或集合；完成后清除节点临时值、集合、调度队列和等待关系，只保留调用方可读取的最终快照、原 context 和必要的失败信息。`CompletableFuture.runAsync` 仅用于需要独立完成信号的整次异步入口，并显式指定 `AsyncExecutorUtils` 的虚拟线程执行器；普通分支不会落入 common pool。虚拟线程不做每个 flow 的池化，实际并发量由用户显式声明的 `asyncThen` 决定；如以后需要限制对外部资源的并发，应另设计限流能力，不靠线程池大小隐式改变调度语义。

仍需作为设计边界观察：节点状态、lane 就绪队列与配对数据已从 `FlowExecutionState` 抽离，复杂的状态转换仍由同一运行锁协调，避免拆分后产生跨锁协议。`asyncThen` 扇出和观察节点数量目前无框架级上限；本轮容量回归未显示立即需要线程池或框架级限流，但虚拟线程仍占用内存，接入外部限额资源时调用方仍需控制并发。阻塞的用户回调可使显式的 `observationCompletion()` 长期未完成，但不拖住节点链路与 `completion()`。当前不增加每流程独立执行器或自定义执行器 API，待出现明确的限流、隔离或追踪需求再收敛契约。

## 5. 状态转换与本轮验证

```text
DECLARED ──依赖全终态──> READY ──提交──> RUNNING
                                            │  ↔ WAITING（awaitState）
                                            ├──> WORK_DONE（本地成功已公布）
                                            │       ├──双方返回成功──> SUCCEEDED
                                            │       └──同伴失败／停止──> FAILED_BY_PEER / STOPPED_NODE / STOPPED_FLOW
                                            └──> SUCCEEDED / SKIPPED（可有显式传值）
                                                 / STOPPED_NODE / STOPPED_FLOW / FAILED / FAILED_BY_PEER
RUNNING ──父节点无值──> SKIPPED
DECLARED / READY ──全局停止──> STOPPED_FLOW
```

状态模型测试覆盖：依赖完成后的就绪与并行就绪、局部停止结果传递、成功 `null`、全局停止阻止新启动、组合节点暂存与同伴失败、超时与无限等待、目标提前终态、全局停止唤醒等待、双方检查点互等、进度覆盖与节点／流程耗时、依赖环／缺失引用。`WORK_DONE` 不算终态，也不能提前解锁依赖节点。执行测试另覆盖 `failTogether` 的本地成功可见性、先后失败顺序、双方上报后互等、用户回滚、回滚异常、完成屏障、局部／全局停止及定义级超时，以及既有执行与并发基线。`onStateChange` 测试覆盖单节点绑定、一次注册、最终状态、组合失败、回调异常隔离、慢回调合并、节点完成与回调完成分离以及不同节点回调互不阻塞；计时器测试覆盖一个超时处理阻塞时另一个流程仍能按时触发。

## 6. 后续迭代仍需定死的边界

1. `dependsOn` 默认只等终态，**不隐式要求成功**；否则依赖方拿不到失败／停止结果进行业务决策。是否提供 `requiresSuccess` 作为显式短路策略，可在公开 API 定稿时决定。
2. `dependsOn` 继续只传状态，不提供依赖节点的返回值。若将来确有多个独立节点的值汇合需求，应设计显式的、可计数释放的值依赖 API，而不是让每个普通依赖都长期保留可能很大的返回对象。
3. `failTogether` 已在定义期注册并在两个成员终结前阻止双方后继调度；用户自行在 `awaitTogether()` 后补偿。`publishSuccess()` 之后只宜等待、补偿并返回；若此后再执行可能失败的业务，已取得成功信号的同伴可能无法回到已返回的节点中回滚，框架不能保证事务式撤销。
4. `FlowRun.completion()` 只在全部节点终态后完成。定义级超时会发协作停止信号，但不强杀用户代码。任意用户谓词是否可达无法普遍静态判定，互相无限等待仍可能需要调用方设置 `awaitState` 超时；是否做有限的运行时死锁诊断可后续决定。
