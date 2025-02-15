package io.github.jockerCN.common;

import io.github.jockerCN.type.TypeConvert;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class TransactionProvider {


    public static void setRollbackOnly() {
        getTransactionStatus().setRollbackOnly();
    }

    public static TransactionStatus getTransactionStatus() {
        return TransactionAspectSupport.currentTransactionStatus();
    }


    public static void setIfRollbackOnly() {
        try {
            setRollbackOnly();
        } catch (NoTransactionException execution) {
            //LOOP
        }
    }

    public static void alwaysExecuteIfAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            doAfterCommit(runnable);
        } else {
            runnable.run();
        }
    }

    public static void doAfterCommit(Runnable runnable) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                TransactionSynchronization.super.afterCommit();
                runnable.run();
            }
        });
    }

    public static Object doAfterCommit(FunctionWrapper<?,?> wrapper,Object argument) {
        AtomicReference<Object> atomicReference = new AtomicReference<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                TransactionSynchronization.super.afterCommit();
                atomicReference.set(wrapper.run(TypeConvert.cast(argument)));
            }
        });
        return atomicReference.get();
    }

    public static void alwaysExecuteAfterCompletion(Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            doAfterCompletion(runnable);
        } else {
            runnable.run();
        }
    }

    public static void doAfterCompletion(Runnable runnable) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronization.super.afterCompletion(status);
                runnable.run();
            }
        });
    }
}
