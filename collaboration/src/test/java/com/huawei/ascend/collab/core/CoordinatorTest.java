package com.huawei.ascend.collab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.collab.core.CoordinationEvent.Type;
import com.huawei.ascend.collab.core.WorkResult.Status;
import com.huawei.ascend.collab.sim.ScriptedWorker;
import com.huawei.ascend.collab.sim.ScriptedWorker.Behavior;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinatorTest {

    private static final java.util.function.LongSupplier CLOCK = () -> 1_000_000L;

    private static Coordinator coordinator(Worker... workers) {
        return new Coordinator(List.of(workers), ResultValidator.nonEmptyOutput(), "test", CLOCK);
    }

    private static boolean hasEvent(CollaborationResult r, Type type) {
        return r.log().stream().anyMatch(e -> e.type() == type);
    }

    @Test
    void completesAndValidates() {
        var r = coordinator(ScriptedWorker.of("w", "sum", Behavior.COMPLETE))
                .run(List.of(SubTask.of("t1", "sum", "x")));
        assertEquals(Status.COMPLETED, r.outcomes().get("t1"));
        assertTrue(hasEvent(r, Type.VALIDATE_OK));
        assertTrue(r.allCompleted());
    }

    @Test
    void rejectsForgedToken() {
        var r = coordinator(ScriptedWorker.of("evil", "sum", Behavior.BAD_TOKEN))
                .run(List.of(new SubTask("t1", "sum", "x", 1, 30_000)));
        assertEquals(Status.REJECTED, r.outcomes().get("t1"));
        assertTrue(hasEvent(r, Type.TOKEN_REJECT));
    }

    @Test
    void reclaimsThenSucceeds() {
        var r = coordinator(ScriptedWorker.flaky("flaky", "sum", 2))
                .run(List.of(new SubTask("t1", "sum", "x", 3, 30_000)));
        assertEquals(Status.COMPLETED, r.outcomes().get("t1"));
        assertTrue(hasEvent(r, Type.RECLAIM));
    }

    @Test
    void handsOverToAnotherCapability() {
        var r = coordinator(
                ScriptedWorker.handingOverTo("triage", "triage", "specialist"),
                ScriptedWorker.of("spec", "specialist", Behavior.COMPLETE))
                .run(List.of(SubTask.of("t1", "triage", "case")));
        assertEquals(Status.COMPLETED, r.outcomes().get("t1"));
        assertTrue(hasEvent(r, Type.HANDOVER));
    }

    @Test
    void failsWhenNoCapableWorker() {
        var r = coordinator(ScriptedWorker.of("w", "sum", Behavior.COMPLETE))
                .run(List.of(SubTask.of("t1", "exotic", "x")));
        assertEquals(Status.FAILED, r.outcomes().get("t1"));
        assertTrue(hasEvent(r, Type.NO_WORKER));
    }

    @Test
    void reclaimsOnValidationFailure() {
        var r = coordinator(ScriptedWorker.of("empty", "sum", Behavior.EMPTY_OUTPUT))
                .run(List.of(new SubTask("t1", "sum", "x", 1, 30_000)));
        assertEquals(Status.FAILED, r.outcomes().get("t1"));
        assertTrue(hasEvent(r, Type.VALIDATE_FAIL));
    }
}
