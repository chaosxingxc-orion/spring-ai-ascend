package com.huawei.ascend.collab.eval;

import com.huawei.ascend.collab.sim.ScriptedWorker;
import com.huawei.ascend.collab.sim.ScriptedWorker.Behavior;
import java.util.List;
import java.util.Set;

/**
 * JSON-serializable spec for a scripted worker in an eval scenario. Turns into a
 * deterministic {@link ScriptedWorker} at run time.
 */
public record WorkerSpec(
        String id,
        List<String> capabilities,
        String behavior,
        String handoverTarget,
        int flakyFailures) {

    public ScriptedWorker toWorker() {
        return new ScriptedWorker(id, Set.copyOf(capabilities),
                Behavior.valueOf(behavior), handoverTarget, flakyFailures);
    }

    public static WorkerSpec of(String id, String capability, Behavior behavior) {
        return new WorkerSpec(id, List.of(capability), behavior.name(), null, 0);
    }

    public static WorkerSpec handover(String id, String capability, String target) {
        return new WorkerSpec(id, List.of(capability), Behavior.HANDOVER.name(), target, 0);
    }

    public static WorkerSpec flaky(String id, String capability, int failures) {
        return new WorkerSpec(id, List.of(capability), Behavior.FLAKY_THEN_OK.name(), null, failures);
    }
}
