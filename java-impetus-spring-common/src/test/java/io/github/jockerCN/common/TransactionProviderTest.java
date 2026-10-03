package io.github.jockerCN.common;

import io.github.jockerCN.JavaImpetusSpringAutoConfiguration;
import io.github.jockerCN.Result;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionProviderTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavaImpetusSpringAutoConfiguration.class))
            .withUserConfiguration(TransactionConfiguration.class);

    @Test
    void callbacksFollowCommitAndRollbackAndNeverRunEarly() {
        runner.run(context -> {
            SpringExecutorHandle handle = context.getBean(SpringExecutorHandle.class);
            RecordingTransactionManager manager = context.getBean(RecordingTransactionManager.class);
            List<String> calls = new ArrayList<>();

            handle.execute((Runnable) () -> {
                assertTrue(TransactionProvider.isTransactionActive());
                TransactionProvider.doAfterCommit(() -> calls.add("commit"));
                TransactionProvider.doAfterRollback(() -> calls.add("rollback"));
                TransactionProvider.doAfterCompletion(status -> calls.add("done:" + status));
                assertTrue(calls.isEmpty());
            });
            assertEquals(List.of("commit", "done:" + TransactionSynchronization.STATUS_COMMITTED), calls);
            assertEquals(1, manager.commits.get());

            calls.clear();
            assertThrows(IllegalStateException.class, () -> handle.execute((Runnable) () -> {
                TransactionProvider.doAfterRollback(() -> calls.add("rollback"));
                throw new IllegalStateException("fail");
            }));
            assertEquals(List.of("rollback"), calls);
            assertEquals(1, manager.rollbacks.get());

            assertThrows(IllegalStateException.class, () -> TransactionProvider.doAfterCommit(() -> {}));
            TransactionProvider.alwaysExecuteIfAfterCommit(() -> calls.add("outside"));
            assertEquals("outside", calls.getLast());
        });
    }

    @Test
    void transactionalWrapperKeepsTheDeclaredReturnType() {
        runner.run(context -> {
            SpringExecutorHandle handle = context.getBean(SpringExecutorHandle.class);
            RecordingTransactionManager manager = context.getBean(RecordingTransactionManager.class);
            List<String> afterCommit = new ArrayList<>();

            assertEquals("value", handle.execute(() -> "value"));
            assertEquals("done", handle.executeAfterCommit("do", value -> value + "ne", afterCommit::add));
            assertEquals(List.of("done"), afterCommit);

            Result<String> failed = handle.executeResult(() -> {
                throw new IllegalArgumentException("bad input");
            });
            assertFalse(failed.isOk());
            assertEquals("bad input", failed.getMessage());
            assertEquals(1, manager.rollbacks.get());
            assertEquals(2, manager.commits.get());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits.incrementAndGet();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks.incrementAndGet();
        }
    }
}
