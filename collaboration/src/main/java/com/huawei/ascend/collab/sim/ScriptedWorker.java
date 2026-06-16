package com.huawei.ascend.collab.sim;

import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import com.huawei.ascend.collab.core.Worker;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A deterministic, no-LLM worker for the eval harness and tests. Its {@link Behavior}
 * is scripted so a scenario can exercise every collaboration path — including
 * adversarial ones (a worker that returns a forged/absent token, or empty output).
 *
 * <p>{@code FLAKY_THEN_OK} fails the first N attempts then succeeds, to exercise
 * reclaim-and-retry convergence.
 */
public final class ScriptedWorker implements Worker {

    public enum Behavior {
        COMPLETE,        // valid token echo + good output
        FAIL,            // valid token echo + FAILED
        TIMEOUT,         // valid token echo + TIMEOUT
        EMPTY_OUTPUT,    // valid token echo + COMPLETED but blank output (fails validation)
        BAD_TOKEN,       // COMPLETED but echoes a forged token (fails token verification)
        NO_TOKEN,        // COMPLETED but echoes no token at all
        HANDOVER,        // hands the task to another capability
        INPUT_REQUIRED,  // needs human input
        FLAKY_THEN_OK    // FAILED for the first `flakyFailures` attempts, then COMPLETE
    }

    private final String id;
    private final Set<String> capabilities;
    private final Behavior behavior;
    private final String handoverTarget;
    private final int flakyFailures;
    private final AtomicInteger calls = new AtomicInteger();

    public ScriptedWorker(String id, Set<String> capabilities, Behavior behavior,
            String handoverTarget, int flakyFailures) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.behavior = behavior;
        this.handoverTarget = handoverTarget;
        this.flakyFailures = flakyFailures;
    }

    public static ScriptedWorker of(String id, String capability, Behavior behavior) {
        return new ScriptedWorker(id, Set.of(capability), behavior, null, 0);
    }

    public static ScriptedWorker handingOverTo(String id, String capability, String targetCapability) {
        return new ScriptedWorker(id, Set.of(capability), Behavior.HANDOVER, targetCapability, 0);
    }

    public static ScriptedWorker flaky(String id, String capability, int failuresBeforeOk) {
        return new ScriptedWorker(id, Set.of(capability), Behavior.FLAKY_THEN_OK, null, failuresBeforeOk);
    }

    /** Number of times this worker was dispatched to — lets tests assert dedupe/budget effects. */
    public int calls() {
        return calls.get();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> capabilities() {
        return capabilities;
    }

    @Override
    public WorkResult execute(SubTask task, TaskToken token) {
        int n = calls.incrementAndGet();
        return switch (behavior) {
            case COMPLETE -> WorkResult.completed(task.id(), "result-of-" + task.id(), token, id);
            case FAIL -> WorkResult.failed(task.id(), token, id, "scripted failure");
            case TIMEOUT -> WorkResult.timeout(task.id(), token, id);
            case EMPTY_OUTPUT -> WorkResult.completed(task.id(), "", token, id);
            case BAD_TOKEN -> WorkResult.completed(task.id(), "result", forge(token), id);
            case NO_TOKEN -> WorkResult.completed(task.id(), "result", null, id);
            case HANDOVER -> WorkResult.handedOver(task.id(), token, id, handoverTarget);
            case INPUT_REQUIRED -> new WorkResult(task.id(), WorkResult.Status.INPUT_REQUIRED,
                    null, token, id, null, "needs input");
            case FLAKY_THEN_OK -> n <= flakyFailures
                    ? WorkResult.failed(task.id(), token, id, "flaky attempt " + n)
                    : WorkResult.completed(task.id(), "result-of-" + task.id(), token, id);
        };
    }

    /** A forged token with a different id — must fail the coordinator's verification. */
    private static TaskToken forge(TaskToken real) {
        return new TaskToken(UUID.randomUUID(), real.taskId(), real.capability(),
                real.assignedAgentId(), real.tenantId(), UUID.randomUUID(),
                real.deadlineEpochMs(), real.issuedAtEpochMs());
    }
}
