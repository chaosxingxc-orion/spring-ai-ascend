package com.huawei.ascend.a2a.memory.hook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceMemoryKit;
import com.huawei.ascend.a2a.memory.experience.InMemoryExperienceStore;
import com.huawei.ascend.a2a.memory.experience.Lesson;
import com.huawei.ascend.a2a.memory.privacy.DefaultPiiRedactor;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Run-end hook distills a blackboard into PII-stripped experience and releases it. */
class CollaborationMemoryHookTest {

    @Test
    void onCollaborationEndDistillsBlackboardIntoExperience() {
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(
                new InMemorySharedMemoryStore(() -> 1_000L), "demo-tenant", "collab-1");
        board.put("riskAssessment", "C3 conservative", "risk-agent");
        board.put("contact", "reach client at advisor@bank.com", "advice-agent"); // carries PII

        InMemoryExperienceStore expStore = new InMemoryExperienceStore();
        ExperienceMemoryKit exp = ExperienceMemoryKit.forTenant(
                expStore, new DefaultPiiRedactor(), "demo-tenant", () -> 1_000L);
        CollaborationSignature sig = new CollaborationSignature(Set.of("risk", "advice"), "wealth-advice");

        new DefaultCollaborationMemoryHook(exp, true).onCollaborationEnd(sig, board);

        List<Lesson> lessons = exp.recall(sig, 10);
        assertFalse(lessons.isEmpty(), "blackboard distilled into experience");
        assertTrue(lessons.stream().anyMatch(l -> l.text().contains("riskAssessment")), "key carried into lesson");
        assertTrue(lessons.stream().noneMatch(l -> l.text().contains("advisor@bank.com")), "PII stripped on the way");
        assertTrue(board.keys().isEmpty(), "blackboard released after distillation");
    }

    @Test
    void noopHookDoesNothing() {
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(
                new InMemorySharedMemoryStore(() -> 1_000L), "t", "c");
        board.put("k", "v", "a");
        CollaborationMemoryHook.NOOP.onCollaborationEnd(
                new CollaborationSignature(Set.of("x"), "y"), board);
        assertFalse(board.keys().isEmpty(), "noop leaves the blackboard intact");
    }
}
