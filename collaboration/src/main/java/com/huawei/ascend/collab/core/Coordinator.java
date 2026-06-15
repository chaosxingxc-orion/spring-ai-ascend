package com.huawei.ascend.collab.core;

import com.huawei.ascend.collab.core.CoordinationEvent.Type;
import com.huawei.ascend.collab.core.WorkResult.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Deterministic given the workers and a clock — which is what makes the eval harness reproducible.
 */
public final class Coordinator {

    private static final int HANDOVER_CAP = 6;

    private final List<Worker> workers;
    private final ResultValidator validator;
    private final String tenantId;
    private final LongSupplier clock;
    private final Map<String, Integer> roundRobin = new LinkedHashMap<>();

    public Coordinator(List<Worker> workers, ResultValidator validator, String tenantId, LongSupplier clock) {
        this.workers = List.copyOf(workers);
        this.validator = validator == null ? ResultValidator.nonEmptyOutput() : validator;
        this.tenantId = tenantId == null ? "default" : tenantId;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public Coordinator(List<Worker> workers) {
        this(workers, ResultValidator.nonEmptyOutput(), "default", System::currentTimeMillis);
    }

    public CollaborationResult run(List<SubTask> tasks) {
        Map<String, Status> outcomes = new LinkedHashMap<>();
        List<CoordinationEvent> log = new ArrayList<>();
        for (SubTask task : tasks) {
            outcomes.put(task.id(), runOne(task, log));
        }
        return new CollaborationResult(outcomes, log);
    }

    private Status runOne(SubTask task, List<CoordinationEvent> log) {
        UUID idemKey = UUID.randomUUID();      // stable across this task's redispatch lineage
        String capability = task.capability();
        String lastWorkerId = null;
        int attempts = 0;
        int handovers = 0;

        while (true) {
            Worker w = pick(capability, lastWorkerId);
            if (w == null) {
                log.add(CoordinationEvent.of(task.id(), Type.NO_WORKER, null, "capability=" + capability));
                return Status.FAILED;
            }
            TaskToken token = TaskToken.issue(task.id(), capability, w.id(), tenantId, idemKey, task.ttlMs(), now());
            log.add(CoordinationEvent.of(task.id(), Type.DISPATCH, w.id(),
                    "capability=" + capability + " token=" + token.tokenId()));
            lastWorkerId = w.id();

            WorkResult r;
            try {
                r = w.execute(task, token);
            } catch (Exception e) {
                r = WorkResult.failed(task.id(), token, w.id(), "exception: " + e.getClass().getSimpleName());
            }

            // 令牌响应校验: the worker must present back a valid, matching token.
            if (!tokenMatches(token, r.echoedToken())) {
                log.add(CoordinationEvent.of(task.id(), Type.TOKEN_REJECT, w.id(), "invalid/absent token echo"));
                if (++attempts < task.maxAttempts()) {
                    log.add(CoordinationEvent.of(task.id(), Type.RECLAIM, w.id(), "after token reject"));
                    continue;
                }
                log.add(CoordinationEvent.of(task.id(), Type.FAIL, w.id(), "token rejected, attempts exhausted"));
                return Status.REJECTED;
            }

            switch (r.status()) {
                case COMPLETED -> {
                    if (validator.isValid(task, r)) {
                        log.add(CoordinationEvent.of(task.id(), Type.VALIDATE_OK, w.id(), null));
                        log.add(CoordinationEvent.of(task.id(), Type.COMPLETE, w.id(), null));
                        return Status.COMPLETED;
                    }
                    log.add(CoordinationEvent.of(task.id(), Type.VALIDATE_FAIL, w.id(), "result rejected by validator"));
                    if (++attempts < task.maxAttempts()) {
                        log.add(CoordinationEvent.of(task.id(), Type.RECLAIM, w.id(), "after validate fail"));
                        continue;
                    }
                    log.add(CoordinationEvent.of(task.id(), Type.FAIL, w.id(), "validation failed, attempts exhausted"));
                    return Status.FAILED;
                }
                case HANDED_OVER -> {
                    if (r.handoverCapability() == null || ++handovers > HANDOVER_CAP) {
                        log.add(CoordinationEvent.of(task.id(), Type.FAIL, w.id(), "hand-over loop or no target"));
                        return Status.FAILED;
                    }
                    log.add(CoordinationEvent.of(task.id(), Type.HANDOVER, w.id(), "to=" + r.handoverCapability()));
                    capability = r.handoverCapability();
                    lastWorkerId = null; // allow any worker of the new capability
                }
                case TIMEOUT, FAILED -> {
                    log.add(CoordinationEvent.of(task.id(), Type.RECLAIM, w.id(),
                            r.status() + (r.detail() == null ? "" : ": " + r.detail())));
                    if (++attempts < task.maxAttempts()) {
                        continue;
                    }
                    log.add(CoordinationEvent.of(task.id(), Type.FAIL, w.id(), "attempts exhausted"));
                    return r.status();
                }
                case INPUT_REQUIRED -> {
                    log.add(CoordinationEvent.of(task.id(), Type.INPUT_REQUIRED, w.id(), "needs human input"));
                    return Status.INPUT_REQUIRED;
                }
                case REJECTED -> {
                    log.add(CoordinationEvent.of(task.id(), Type.FAIL, w.id(), "worker reported rejected"));
                    return Status.REJECTED;
                }
                default -> {
                    return Status.FAILED;
                }
            }
        }
    }

    /** Round-robin among workers handling the capability, preferring one != excludeId for retry diversity. */
    private Worker pick(String capability, String excludeId) {
        List<Worker> candidates = new ArrayList<>();
        for (Worker w : workers) {
            if (w.handles(capability)) {
                candidates.add(w);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (excludeId != null && candidates.size() > 1) {
            candidates.removeIf(w -> w.id().equals(excludeId));
        }
        int idx = roundRobin.merge(capability, 1, Integer::sum) - 1;
        return candidates.get(Math.floorMod(idx, candidates.size()));
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
