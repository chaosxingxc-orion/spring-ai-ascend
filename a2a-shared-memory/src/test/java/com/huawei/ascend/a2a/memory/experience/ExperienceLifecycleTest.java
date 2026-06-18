package com.huawei.ascend.a2a.memory.experience;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.hook.CollaborationMemoryHook;
import com.huawei.ascend.a2a.memory.hook.DefaultCollaborationMemoryHook;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Cross-run experience end-to-end: a (simulated) collaboration coordinator runs a
 * collaboration, then calls the run-end hook; a LATER collaboration with the same
 * signature recalls what worked. Proves the hook integration — the experience
 * layer accumulates across runs, not just unit-tested in isolation.
 */
class ExperienceLifecycleTest {

    private static final String TENANT = "bank";
    private static final CollaborationSignature SIG =
            new CollaborationSignature(Set.of("risk", "loan"), "wealth-advice");

    @Test
    void laterRunRecallsAnEarlierRunsExperience() {
        SharedMemoryStore shared = new InMemorySharedMemoryStore(() -> 1L);
        ExperienceStore expStore = new InMemoryExperienceStore();
        ExperienceMemoryKit experience = ExperienceMemoryKit.forTenant(expStore, TENANT);
        CollaborationMemoryHook hook = new DefaultCollaborationMemoryHook(experience, true);

        // ---- RUN 1: a collaboration produces conclusions, then the coordinator ends it ----
        SharedMemoryKit run1 = SharedMemoryKit.forCollaboration(shared, TENANT, "run-1");
        run1.put("riskAssessment", "C3 medium suited the client", "risk-agent");
        run1.put("loanDecision", "approved within limit", "loan-agent");
        hook.onCollaborationEnd(SIG, run1);                  // <-- the coordinator run-end seam

        assertTrue(run1.keys().isEmpty(), "blackboard released after distillation");
        assertFalse(experience.recall(SIG, 10).isEmpty(), "run 1 distilled into experience");

        // ---- RUN 2: a new collaboration with the same signature recalls what worked ----
        List<Lesson> priorExperience = experience.recall(SIG, 10);
        assertTrue(priorExperience.stream().anyMatch(l -> l.text().contains("C3 medium")),
                "run 2 recalls run 1's risk lesson");
        assertTrue(priorExperience.stream().anyMatch(l -> l.text().contains("approved")),
                "run 2 recalls run 1's loan lesson");
    }

    @Test
    void experienceIsTenantIsolated() {
        ExperienceStore expStore = new InMemoryExperienceStore();
        SharedMemoryStore shared = new InMemorySharedMemoryStore(() -> 1L);
        CollaborationMemoryHook bankHook =
                new DefaultCollaborationMemoryHook(ExperienceMemoryKit.forTenant(expStore, "bank"), true);

        SharedMemoryKit run = SharedMemoryKit.forCollaboration(shared, "bank", "r1");
        run.put("riskAssessment", "bank-only insight", "risk-agent");
        bankHook.onCollaborationEnd(SIG, run);

        ExperienceMemoryKit otherTenant = ExperienceMemoryKit.forTenant(expStore, "other-bank");
        assertTrue(otherTenant.recall(SIG, 10).isEmpty(), "another tenant sees no experience");
    }
}
