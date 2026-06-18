package com.huawei.ascend.collab.core;

import com.huawei.ascend.collab.core.CoordinationEvent.Type;
import com.huawei.ascend.collab.core.WorkResult.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * The multi-agent coordinator. Implements the collaboration protocol on top of a
 * transport-agnostic {@link Worker} set:
 *
 * <ul>
 *   <li><b>分发 distribution</b> — routes each sub-task to a worker that handles its capability;</li>
 *   <li><b>任务令牌 task token</b> — issues a {@link TaskToken} per dispatch and <b>令牌响应校验</b>
 *       verifies the worker echoes a valid, matching, unexpired token (else REJECT);</li>
 *   <li><b>hand-over</b> — a worker may hand the task to another capability; the coordinator re-dispatches;</li>
 *   <li><b>回收 reclaim</b> — on timeout/failure/validation-fail it reclaims and redispatches (to a different
 *       worker when possible) up to {@code maxAttempts};</li>
 *   <li><b>校验 validation</b> — a {@link ResultValidator} gates COMPLETED results.</li>
 * </ul>
 *
 * <p>Backpressure & token economy are governed by {@link CoordinatorConfig}: retry backoff
 * (don't hammer a failing agent), a per-batch dispatch budget (cap token spend), bounded
 * concurrency (cap in-flight load), and result dedupe (skip re-running identical work).
 *
 * <p><b>Dedupe scope.</b> The dedupe cache keys on {@code (capability, payload)} and is written
 * only when a task <i>completes</i>. It therefore guarantees zero-extra-token reuse for
 * sequential {@link #run} (the second identical task sees the first's completion). Under
 * {@link #runConcurrent} it is <b>best-effort</b>: identical tasks already in flight before the
 * first completes each dispatch (no per-key in-flight join — kept simple so a failing leader
 * never wrongly dedupes its followers). Need strict concurrent dedupe? Pre-group identical
 * payloads before submitting, or run them sequentially.
 *
 * <p>{@link #run} executes tasks sequentially; {@link #runConcurrent} distributes them in
 * parallel (each task is independent — its own token lineage, attempts, and event log).
 * Deterministic given the workers and a clock with the default config, which is what makes
 * the eval harness reproducible.
 */
public final class Coordinator {

    private static final int HANDOVER_CAP = 6;

    /** Seam so retry backoff is testable without real sleeps. */
    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private final WorkerRegistry registry;
    private final ResultValidator validator;
    private final String tenantId;
    private final LongSupplier clock;
    private final CollaborationObserver observer;
    private final CoordinatorConfig config;
    private final Sleeper sleeper;
    private final CircuitBreaker breaker;
    private final Map<String, Integer> roundRobin = new LinkedHashMap<>();

    /** Canonical constructor. */
    Coordinator(WorkerRegistry registry, ResultValidator validator, String tenantId, LongSupplier clock,
            CollaborationObserver observer, CoordinatorConfig config, Sleeper sleeper) {
        this.registry = registry;
        this.validator = validator == null ? ResultValidator.nonEmptyOutput() : validator;
        this.tenantId = tenantId == null ? "default" : tenantId;
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.observer = observer == null ? CollaborationObserver.NOOP : observer;
        this.config = config == null ? CoordinatorConfig.defaults() : config;
        this.sleeper = sleeper == null ? Thread::sleep : sleeper;
        this.breaker = this.config.circuitFailureThreshold() > 0
                ? new CircuitBreaker(this.config.circuitFailureThreshold(), this.config.circuitCooldownMs(), this.clock)
                : null;
    }

    public Coordinator(WorkerRegistry registry, ResultValidator validator, String tenantId,
            LongSupplier clock, CollaborationObserver observer, CoordinatorConfig config) {
        this(registry, validator, tenantId, clock, observer, config, Thread::sleep);
    }

    /** List-backed canonical form (wraps an {@link InMemoryWorkerRegistry}); the Sleeper seam is for tests. */
    Coordinator(List<Worker> workers, ResultValidator validator, String tenantId, LongSupplier clock,
            CollaborationObserver observer, CoordinatorConfig config, Sleeper sleeper) {
        this(WorkerRegistry.of(workers), validator, tenantId, clock, observer, config, sleeper);
    }

    public Coordinator(List<Worker> workers, ResultValidator validator, String tenantId,
            LongSupplier clock, CollaborationObserver observer, CoordinatorConfig config) {
        this(WorkerRegistry.of(workers), validator, tenantId, clock, observer, config, Thread::sleep);
    }

    public Coordinator(List<Worker> workers, ResultValidator validator, String tenantId,
            LongSupplier clock, CollaborationObserver observer) {
        this(workers, validator, tenantId, clock, observer, CoordinatorConfig.defaults());
    }

    public Coordinator(List<Worker> workers, ResultValidator validator, String tenantId, LongSupplier clock) {
        this(workers, validator, tenantId, clock, CollaborationObserver.NOOP);
    }

    public Coordinator(List<Worker> workers) {
        this(workers, ResultValidator.nonEmptyOutput(), "default", System::currentTimeMillis);
    }

    /** Sequential distribution. */
    public CollaborationResult run(List<SubTask> tasks) {
        BatchState state = new BatchState(config.maxDispatches());
        Map<String, Status> outcomes = new LinkedHashMap<>();
        List<CoordinationEvent> log = new ArrayList<>();
        for (SubTask task : tasks) {
            TaskRun run = runOne(task, state);
            outcomes.put(task.id(), run.status());
            log.addAll(run.events());
        }
        return new CollaborationResult(outcomes, log);
    }

    /**
     * Concurrent distribution: tasks run in parallel, bounded by {@code parallelism} (and by
     * {@link CoordinatorConfig#maxConcurrency()} when set). The executor uses a bounded queue
     * with a caller-runs policy, so a producer that outruns the pool is throttled rather than
     * piling unbounded work onto the heap (backpressure).
     */
    public CollaborationResult runConcurrent(List<SubTask> tasks, int parallelism) {
        int requested = config.maxConcurrency() > 0 ? Math.min(parallelism, config.maxConcurrency()) : parallelism;
        int pool = Math.max(1, Math.min(requested, Math.max(1, tasks.size())));
        ThreadPoolExecutor exec = new ThreadPoolExecutor(pool, pool, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, pool)), new ThreadPoolExecutor.CallerRunsPolicy());
        BatchState state = new BatchState(config.maxDispatches());
        try {
            Map<String, Future<TaskRun>> futures = new LinkedHashMap<>();
            for (SubTask task : tasks) {
                futures.put(task.id(), exec.submit(() -> runOne(task, state)));
            }
            Map<String, Status> outcomes = new LinkedHashMap<>();
            List<CoordinationEvent> log = new ArrayList<>();
            futures.forEach((taskId, future) -> {
                try {
                    TaskRun run = future.get();
                    outcomes.put(taskId, run.status());
                    log.addAll(run.events());
                } catch (Exception e) {
                    outcomes.put(taskId, Status.FAILED);
                    log.add(CoordinationEvent.of(taskId, Type.FAIL, null, "coordinator error: " + e.getMessage()));
                }
            });
            return new CollaborationResult(outcomes, log);
        } finally {
            exec.shutdownNow();
        }
    }

    private record TaskRun(Status status, List<CoordinationEvent> events) {
    }

    /** Per-batch shared state: the token-economy dispatch budget and the dedupe result cache. */
    private static final class BatchState {
        private final boolean unlimited;
        private final AtomicInteger dispatchesLeft;
        private final ConcurrentMap<String, Boolean> completed = new ConcurrentHashMap<>();

        BatchState(int maxDispatches) {
            this.unlimited = maxDispatches <= 0;
            this.dispatchesLeft = new AtomicInteger(unlimited ? 0 : maxDispatches);
        }

        /** Reserve one dispatch against the budget; false once the batch budget is spent. */
        boolean tryDispatch() {
            return unlimited || dispatchesLeft.getAndDecrement() > 0;
        }
    }

    private TaskRun runOne(SubTask task, BatchState state) {
        List<CoordinationEvent> log = new ArrayList<>();
        long started = now();
        Status status = drive(task, log, state);
        observer.onTaskCompleted(task.id(), status, now() - started);
        return new TaskRun(status, log);
    }

    private Status drive(SubTask task, List<CoordinationEvent> log, BatchState state) {
        String dedupeKey = task.capability() + " " + task.payload();
        if (config.dedupeResults() && state.completed.containsKey(dedupeKey)) {
            // Identical work already completed in this batch — reuse it, spend no tokens.
            add(log, task.id(), Type.VALIDATE_OK, null, "deduped");
            add(log, task.id(), Type.COMPLETE, null, "deduped: token saved");
            return Status.COMPLETED;
        }

        UUID idemKey = UUID.randomUUID();      // stable across this task's redispatch lineage
        String capability = task.capability();
        String lastWorkerId = null;
        int attempts = 0;
        int handovers = 0;

        while (true) {
            Worker w = pick(capability, lastWorkerId);
            if (w == null) {
                add(log, task.id(), Type.NO_WORKER, null, "capability=" + capability);
                return Status.FAILED;
            }
            if (!state.tryDispatch()) {
                // Token-economy budget for the whole batch is exhausted — fail fast rather
                // than letting every remaining task burn its full maxAttempts.
                add(log, task.id(), Type.FAIL, w.id(), "dispatch budget exhausted");
                return Status.FAILED;
            }
            TaskToken token = TaskToken.issue(task.id(), capability, w.id(), tenantId, idemKey, task.ttlMs(), now());
            add(log, task.id(), Type.DISPATCH, w.id(), "capability=" + capability + " token=" + token.tokenId());
            lastWorkerId = w.id();

            WorkResult r;
            try {
                r = w.execute(task, token);
            } catch (Exception e) {
                r = WorkResult.failed(task.id(), token, w.id(), "exception: " + e.getClass().getSimpleName());
            }

            if (!tokenMatches(token, r.echoedToken())) {
                recordFailure(w.id());
                add(log, task.id(), Type.TOKEN_REJECT, w.id(), "invalid/absent token echo");
                if (++attempts < task.maxAttempts()) {
                    add(log, task.id(), Type.RECLAIM, w.id(), "after token reject");
                    backoff(attempts);
                    continue;
                }
                add(log, task.id(), Type.FAIL, w.id(), "token rejected, attempts exhausted");
                return Status.REJECTED;
            }

            switch (r.status()) {
                case COMPLETED -> {
                    if (validator.isValid(task, r)) {
                        recordSuccess(w.id());
                        add(log, task.id(), Type.VALIDATE_OK, w.id(), null);
                        add(log, task.id(), Type.COMPLETE, w.id(), null);
                        if (config.dedupeResults()) {
                            state.completed.put(dedupeKey, Boolean.TRUE);
                        }
                        return Status.COMPLETED;
                    }
                    recordFailure(w.id());
                    add(log, task.id(), Type.VALIDATE_FAIL, w.id(), "result rejected by validator");
                    if (++attempts < task.maxAttempts()) {
                        add(log, task.id(), Type.RECLAIM, w.id(), "after validate fail");
                        backoff(attempts);
                        continue;
                    }
                    add(log, task.id(), Type.FAIL, w.id(), "validation failed, attempts exhausted");
                    return Status.FAILED;
                }
                case HANDED_OVER -> {
                    if (r.handoverCapability() == null || ++handovers > HANDOVER_CAP) {
                        add(log, task.id(), Type.FAIL, w.id(), "hand-over loop or no target");
                        return Status.FAILED;
                    }
                    add(log, task.id(), Type.HANDOVER, w.id(), "to=" + r.handoverCapability());
                    capability = r.handoverCapability();
                    lastWorkerId = null;
                }
                case TIMEOUT, FAILED -> {
                    recordFailure(w.id());
                    add(log, task.id(), Type.RECLAIM, w.id(),
                            r.status() + (r.detail() == null ? "" : ": " + r.detail()));
                    if (++attempts < task.maxAttempts()) {
                        backoff(attempts);
                        continue;
                    }
                    add(log, task.id(), Type.FAIL, w.id(), "attempts exhausted");
                    return r.status();
                }
                case INPUT_REQUIRED -> {
                    add(log, task.id(), Type.INPUT_REQUIRED, w.id(), "needs human input");
                    return Status.INPUT_REQUIRED;
                }
                case REJECTED -> {
                    add(log, task.id(), Type.FAIL, w.id(), "worker reported rejected");
                    return Status.REJECTED;
                }
                default -> {
                    return Status.FAILED;
                }
            }
        }
    }

    /** Wait before a reclaim retry so a failing/slow agent is not hammered (negative feedback). */
    private void backoff(int attempt) {
        int ms = config.backoffMs().applyAsInt(attempt);
        if (ms > 0) {
            try {
                sleeper.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void add(List<CoordinationEvent> log, String taskId, Type type, String workerId, String detail) {
        CoordinationEvent e = CoordinationEvent.of(taskId, type, workerId, detail);
        log.add(e);
        observer.onEvent(e);
    }

    /**
     * Round-robin among workers handling the capability, preferring one != excludeId for retry
     * diversity, and skipping unhealthy / circuit-open workers (so load sheds off a dead node).
     * If every candidate is unavailable it falls back to the full set rather than spuriously
     * reporting NO_WORKER — a best-effort attempt beats giving up.
     */
    private synchronized Worker pick(String capability, String excludeId) {
        List<Worker> all = registry.workersFor(capability);
        if (all.isEmpty()) {
            return null;
        }
        List<Worker> candidates = new ArrayList<>();
        for (Worker w : all) {
            if (w.healthy() && !circuitOpen(w.id())) {
                candidates.add(w);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(all); // all down — try anyway rather than fail fast
        }
        if (excludeId != null && candidates.size() > 1) {
            candidates.removeIf(w -> w.id().equals(excludeId));
        }
        int idx = roundRobin.merge(capability, 1, Integer::sum) - 1;
        return candidates.get(Math.floorMod(idx, candidates.size()));
    }

    private boolean circuitOpen(String workerId) {
        return breaker != null && breaker.isOpen(workerId);
    }

    private void recordSuccess(String workerId) {
        if (breaker != null) {
            breaker.recordSuccess(workerId);
        }
    }

    private void recordFailure(String workerId) {
        if (breaker != null) {
            breaker.recordFailure(workerId);
        }
    }

    private boolean tokenMatches(TaskToken issued, TaskToken echoed) {
        if (echoed == null) {
            return false;
        }
        return issued.tokenId().equals(echoed.tokenId())
                && issued.taskId().equals(echoed.taskId())
                && issued.idempotencyKey().equals(echoed.idempotencyKey())
                && !echoed.isExpiredAt(now());
    }

    private long now() {
        return clock.getAsLong();
    }
}
