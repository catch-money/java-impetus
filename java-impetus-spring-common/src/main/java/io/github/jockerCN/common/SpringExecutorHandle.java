package io.github.jockerCN.common;

import io.github.jockerCN.Result;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** A Spring-managed proxy entry point for short transactional operations. */
@SuppressWarnings("unused")
public class SpringExecutorHandle {

    public static SpringExecutorHandle getInstance() {
        return SpringProvider.getBean(SpringExecutorHandle.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeThrows(Runnable runnable) {
        runnable.run();
    }

    /** Propagates failures so Spring can roll the transaction back. */
    @Transactional(rollbackFor = Exception.class)
    public void execute(Runnable runnable) {
        runnable.run();
    }

    @Transactional(rollbackFor = Exception.class)
    public <T> T executeThrows(Supplier<T> supplier) {
        return supplier.get();
    }

    /** Returns the supplier's actual type; never substitutes a Result for T. */
    @Transactional(rollbackFor = Exception.class)
    public <T> T execute(Supplier<T> supplier) {
        return supplier.get();
    }

    /** Returns a typed result while explicitly marking the caught failure for rollback. */
    @Transactional(rollbackFor = Exception.class)
    public <T> Result<T> executeResult(Supplier<T> supplier) {
        try {
            return Result.ok(supplier.get());
        } catch (RuntimeException failure) {
            TransactionProvider.setRollbackOnly();
            return Result.failWithMsg(failure.getMessage());
        }
    }

    /** Schedules a callback with the computed value only after a successful commit. */
    @Transactional(rollbackFor = Exception.class)
    public <T, R> R executeAfterCommit(T input, Function<T, R> action, Consumer<? super R> afterCommit) {
        R result = action.apply(input);
        TransactionProvider.doAfterCommit(() -> afterCommit.accept(result));
        return result;
    }
}
