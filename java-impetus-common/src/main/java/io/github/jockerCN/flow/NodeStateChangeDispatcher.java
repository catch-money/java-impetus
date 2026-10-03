package io.github.jockerCN.flow;

import io.github.jockerCN.async.AsyncExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Per-run observers; each node retains at most one pending update. */
final class NodeStateChangeDispatcher<C> {

    private static final Logger log = LoggerFactory.getLogger(NodeStateChangeDispatcher.class);

    private final Map<String, Observer> observers;
    private final CompletableFuture<Void> completion;

    NodeStateChangeDispatcher(Map<String, NodeStateChangeListener<C>> listeners, C context) {
        Map<String, Observer> compiled = new LinkedHashMap<>();
        listeners.forEach((id, listener) -> compiled.put(id, new Observer(listener, context)));
        observers = Map.copyOf(compiled);
        completion = CompletableFuture.allOf(
                observers.values().stream().map(Observer::completion).toArray(CompletableFuture[]::new));
    }

    void publish(NodeStateChange change) {
        observers.get(change.nodeId()).publish(change);
    }

    void close() {
        observers.values().forEach(Observer::close);
    }

    void abort() {
        observers.values().forEach(Observer::abort);
    }

    CompletableFuture<Void> completion() {
        return completion;
    }

    int pendingCount() {
        return observers.values().stream().mapToInt(Observer::pendingCount).sum();
    }

    private final class Observer {

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition available = lock.newCondition();
        private final NodeStateChangeListener<C> listener;
        private final C context;
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private NodeStateChange pending;
        private boolean started;
        private boolean closed;

        private Observer(NodeStateChangeListener<C> listener, C context) {
            this.listener = listener;
            this.context = context;
        }

        private void publish(NodeStateChange change) {
            boolean launch = false;
            lock.lock();
            try {
                if (closed) {
                    return;
                }
                pending = pending == null ? change : new NodeStateChange(change.nodeId(),
                        pending.previousStatus(), change.current());
                if (!started) {
                    started = true;
                    launch = true;
                }
                available.signal();
            } finally {
                lock.unlock();
            }
            if (launch) {
                try {
                    AsyncExecutorUtils.executor(() -> {
                        try {
                            deliver();
                            finished.complete(null);
                        } catch (Throwable failure) {
                            log.error("Flow state observer terminated unexpectedly", failure);
                            finished.completeExceptionally(failure);
                        }
                    });
                } catch (Throwable failure) {
                    lock.lock();
                    try {
                        pending = null;
                        closed = true;
                    } finally {
                        lock.unlock();
                    }
                    log.error("Flow state observer could not start", failure);
                    finished.completeExceptionally(failure);
                }
            }
        }

        private void close() {
            boolean noWorker;
            lock.lock();
            try {
                closed = true;
                available.signal();
                noWorker = !started;
            } finally {
                lock.unlock();
            }
            if (noWorker) {
                finished.complete(null);
            }
        }

        private void abort() {
            boolean noWorker;
            lock.lock();
            try {
                pending = null;
                closed = true;
                available.signal();
                noWorker = !started;
            } finally {
                lock.unlock();
            }
            if (noWorker) {
                finished.complete(null);
            }
        }

        private CompletableFuture<Void> completion() {
            return finished;
        }

        private int pendingCount() {
            lock.lock();
            try {
                return pending == null ? 0 : 1;
            } finally {
                lock.unlock();
            }
        }

        private void deliver() {
            try {
                while (true) {
                    NodeStateChange change;
                    lock.lockInterruptibly();
                    try {
                        while (pending == null && !closed) {
                            available.await();
                        }
                        if (pending == null) {
                            return;
                        }
                        change = pending;
                        pending = null;
                    } finally {
                        lock.unlock();
                    }
                    try {
                        listener.onStateChange(context, change);
                    } catch (Throwable failure) {
                        log.warn("Node {} onStateChange failed", change.nodeId(), failure);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Flow state observer was interrupted", interrupted);
            }
        }
    }
}
