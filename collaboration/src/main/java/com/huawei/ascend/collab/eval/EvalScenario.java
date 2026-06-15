package com.huawei.ascend.collab.eval;

import com.huawei.ascend.collab.core.CollaborationResult;
import com.huawei.ascend.collab.core.Coordinator;
import com.huawei.ascend.collab.core.ResultValidator;
import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.Worker;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * One multi-task collaboration evaluation case: the sub-tasks, the (scripted)
 * workers, and the expected outcome — both per-task final status and a set of
 * coordination event types that MUST occur (e.g. a flaky scenario must show a
 * RECLAIM; a forged-token scenario must show a TOKEN_REJECT). This is the unit of
 * the eval set ("评测任务编制").
 *
 * @param expectedOutcomes taskId → expected {@code WorkResult.Status} name
 * @param requiredEvents   {@code CoordinationEvent.Type} names that must appear in the log
 */
public record EvalScenario(
        String name,
        String description,
        List<SubTask> tasks,
        List<WorkerSpec> workers,
        Map<String, String> expectedOutcomes,
        List<String> requiredEvents) {

    /** Run this scenario deterministically (fixed clock → tokens never expire mid-run). */
    public CollaborationResult run() {
        LongSupplier fixedClock = () -> 1_000_000L;
        List<Worker> built = workers.stream().map(WorkerSpec::toWorker).map(w -> (Worker) w).toList();
        Coordinator coordinator = new Coordinator(built, ResultValidator.nonEmptyOutput(), "eval-tenant", fixedClock);
        return coordinator.run(tasks);
    }
}
