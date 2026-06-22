package com.bank.financial.research.model;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounds a delegate {@link ReportModel} with a hard per-call timeout. The live
 * LLM path can otherwise hang indefinitely — the framework's model invoke is
 * called with no timeout — so a single stuck generation would block a whole
 * report run past its budget. This decorator runs each generation on a daemon
 * worker and abandons it after {@code timeout}, throwing a
 * {@link ModelTimeoutException} the sub-agents catch and degrade on.
 *
 * <p>The worker pool is a single <b>process-wide, bounded</b> executor shared by
 * every instance. An earlier design gave each instance its own
 * {@code newCachedThreadPool} that was never shut down — and an instance is built
 * per web request, so each live run leaked a pool. A shared pool removes the leak;
 * bounding it (with direct hand-off + abort-on-saturation) means a burst of stuck
 * calls degrades gracefully instead of spawning unbounded threads.
 */
public final class TimeoutReportModel implements ReportModel {

    /**
     * Shared, bounded worker pool. corePoolSize 0 + {@link SynchronousQueue} means
     * each submit either hands off to an idle/new thread or is rejected once
     * {@code maxPoolSize} is reached (no unbounded queue, no unbounded threads);
     * idle threads are reaped after 60s. Daemon so a stuck call never blocks JVM
     * shutdown. Size via {@code RESEARCH_MODEL_POOL_MAX} (default 64).
     */
    private static final ExecutorService POOL = newSharedPool();

    private final ReportModel delegate;
    private final long timeoutMs;

    public TimeoutReportModel(ReportModel delegate, Duration timeout) {
        this.delegate = delegate;
        this.timeoutMs = timeout.toMillis();
    }

    private static ExecutorService newSharedPool() {
        ThreadFactory daemon = r -> {
            Thread t = new Thread(r, "report-model-call");
            t.setDaemon(true); // never block JVM shutdown on a stuck model call
            return t;
        };
        return new ThreadPoolExecutor(0, poolMax(), 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), daemon, new ThreadPoolExecutor.AbortPolicy());
    }

    private static int poolMax() {
        try {
            String v = System.getenv("RESEARCH_MODEL_POOL_MAX");
            return v == null || v.isBlank() ? 64 : Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 64;
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String generate(ModelTask task) {
        Future<String> future;
        try {
            future = POOL.submit(() -> delegate.generate(task));
        } catch (RejectedExecutionException e) {
            // Pool saturated: no capacity to run under timeout protection. Degrade now
            // rather than block — and don't retry (retrying a saturated pool won't help).
            throw new ModelTimeoutException("report model pool saturated (role=" + task.role() + ")");
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ModelTimeoutException(
                    "report model '" + delegate.name() + "' timed out after " + timeoutMs + "ms (role="
                            + task.role() + ")");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ModelTimeoutException("report model call interrupted (role=" + task.role() + ")");
        } catch (java.util.concurrent.ExecutionException e) {
            // Surface the delegate's real failure for the caller to degrade on.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException("report model '" + delegate.name() + "' failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * Thrown when a generation exceeds its hard time budget (or the worker pool is
     * saturated). {@link NonRetryable}: the time budget is already spent, so a retry
     * only multiplies the wall-clock — the sub-agent's degrade path applies instead.
     */
    public static final class ModelTimeoutException extends RuntimeException implements NonRetryable {
        public ModelTimeoutException(String message) {
            super(message);
        }
    }
}
