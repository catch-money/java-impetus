package io.github.jockerCN.common;

import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Transaction-bound callbacks run on the transaction completion thread. */
public final class TransactionProvider {

    private TransactionProvider() {
    }

    public static boolean isTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    public static TransactionStatus getTransactionStatus() {
        return TransactionAspectSupport.currentTransactionStatus();
    }

    public static void setRollbackOnly() {
        getTransactionStatus().setRollbackOnly();
    }

    public static void setIfRollbackOnly() {
        try {
            setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // No proxied transaction is bound to this thread.
        }
    }

    public static void doAfterCommit(Runnable action) {
        requireSynchronization();
        Objects.requireNonNull(action, "action");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public static void alwaysExecuteIfAfterCommit(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (hasSynchronization()) {
            doAfterCommit(action);
        } else {
            action.run();
        }
    }

    public static void doAfterRollback(Runnable action) {
        requireSynchronization();
        Objects.requireNonNull(action, "action");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }

    public static void doAfterCompletion(Runnable action) {
        Objects.requireNonNull(action, "action");
        doAfterCompletion(status -> action.run());
    }

    public static void doAfterCompletion(IntConsumer action) {
        requireSynchronization();
        Objects.requireNonNull(action, "action");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status);
            }
        });
    }

    public static void alwaysExecuteAfterCompletion(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (hasSynchronization()) {
            doAfterCompletion(action);
        } else {
            action.run();
        }
    }

    private static boolean hasSynchronization() {
        return isTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private static void requireSynchronization() {
        if (!hasSynchronization()) {
            throw new IllegalStateException("An active Spring transaction with synchronization is required");
        }
    }
}
