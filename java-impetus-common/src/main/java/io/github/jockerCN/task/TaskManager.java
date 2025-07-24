//package io.github.jockerCN.task;
//
//
//import io.github.jockerCN.async.AsyncExecutorUtils;
//import io.github.jockerCN.function.FunctionWrapper;
//import io.github.jockerCN.type.TypeConvert;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.function.Function;
//import java.util.function.Supplier;
//
//@SuppressWarnings("unused")
//public class TaskManager {
//
//    private final List<Task<?, ?>> taskPhases = new ArrayList<>();
//
//    public <T, R> Task<T, R> add(Runnable runnable) {
//        Task<T, R> task = new Task<>(this, runnable);
//        taskPhases.add(task);
//        return task;
//    }
//
//    public <T, R> Task<T, R> add(Supplier<T> supplier) {
//        Task<T, R> task = new Task<>(this, supplier);
//        taskPhases.add(task);
//        return task;
//    }
//
//    public <T, R> Task<T, R> add(Function<T, R> function) {
//        Task<T, R> task = new Task<>(this, function);
//        taskPhases.add(task);
//        return task;
//    }
//
//    public Object start() {
//        Object result = null;
//        for (Task<?, ?> taskPhase : taskPhases) {
//            result = taskPhase.execute(result);
//        }
//        return result;
//    }
//
//
//    public static class Task<T, R> {
//        private final TaskManager taskManager;
//        private final FunctionWrapper<?, ?> taskWrapper;  // 任务包装器
//        //        private FunctionWrapper<?, ?> afterCommitWrapper;
////        private TaskPhase phase = TaskPhase.NO_TRANSACTION;  // 默认无事务
//        private ExecutionMode executionMode = ExecutionMode.SYNC;
//
//        public Task(TaskManager taskManager, Runnable runnable) {
//            this.taskManager = taskManager;
//            this.taskWrapper = FunctionWrapper.wrap(runnable);
//        }
//
//        public Task(TaskManager taskManager, Supplier<T> supplier) {
//            this.taskManager = taskManager;
//            this.taskWrapper = FunctionWrapper.wrap(supplier);
//        }
//
//        public Task(TaskManager taskManager, Function<T, R> function) {
//            this.taskManager = taskManager;
//            this.taskWrapper = FunctionWrapper.wrap(function);
//        }
//
//        private Task<T, R> syncExecution() {
//            this.executionMode = ExecutionMode.SYNC;
//            return this;
//        }
//
//        // 设置执行方式为异步
//        private Task<T, R> asyncExecution() {
//            this.executionMode = ExecutionMode.ASYNC;
//            return this;
//        }
//
//     /*   public TransactionPhaseManager inTransaction() {
//            this.phase = TaskPhase.IN_TRANSACTION;
//            return new TransactionPhaseManager(this);
//        }*/
//
//
///*        public ExecutionModeManager noTransaction() {
//            this.phase = TaskPhase.NO_TRANSACTION;
//            return new ExecutionModeManager(this);
//        }*/
//
///*        private ExecutionModeManager afterCommit(FunctionWrapper<?, ?> functionWrapper) {
//            this.afterCommitWrapper = functionWrapper;
//            return new ExecutionModeManager(this);
//        }*/
//
//
//        private TaskManager end() {
//            return taskManager;
//        }
//
//        protected Object execute(Object object) {
//            if (this.executionMode == ExecutionMode.ASYNC) {
//                return AsyncExecutorUtils.executor(() -> taskWrapper.run(TypeConvert.cast(object)));
//
//            }
//            return taskWrapper.run(TypeConvert.cast(object));
//        }
//
//
//       /* public static class TransactionPhaseManager {
//
//            private final Task<?, ?> task;
//
//            public <T, R> TransactionPhaseManager(Task<T, R> task) {
//                this.task = task;
//            }
//
//            public <T> ExecutionModeManager noAfterCommit() {
//                return new ExecutionModeManager(task);
//            }
//
//            public <T> ExecutionModeManager afterCommit(Supplier<T> supplier) {
//                return task.afterCommit(FunctionWrapper.wrap(supplier));
//            }
//
//            public <T, R> ExecutionModeManager afterCommit(Function<T, R> function) {
//                return task.afterCommit(FunctionWrapper.wrap(function));
//            }
//
//            public ExecutionModeManager afterCommit(Runnable runnable) {
//                return task.afterCommit(FunctionWrapper.wrap(runnable));
//            }
//        }*/
//
//        public static class ExecutionModeManager {
//
//            private final Task<?, ?> task;
//
//            public <T, R> ExecutionModeManager(Task<T, R> task) {
//                this.task = task;
//            }
//
//            public TaskManager syncExecution() {
//                return task.syncExecution().end();
//            }
//
//
//            public TaskManager asyncExecution() {
//                return task.asyncExecution().end();
//            }
//
//        }
//
//        public enum ExecutionMode {
//            SYNC,  // 同步执行
//            ASYNC  // 异步执行
//        }
//
//        public enum TaskPhase {
//            IN_TRANSACTION,  // 事务中执行
//            AFTER_COMMIT,    // 提交后执行
//            NO_TRANSACTION   // 无事务执行
//        }
//    }
//
//}