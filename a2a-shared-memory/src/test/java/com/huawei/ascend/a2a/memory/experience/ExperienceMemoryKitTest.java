package com.huawei.ascend.a2a.memory.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.privacy.DefaultPiiRedactor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cross-run experience: PII-stripped on record, recalled by signature relevance, tenant-isolated. */
class ExperienceMemoryKitTest {

    private static final CollaborationSignature WEALTH =
            new CollaborationSignature(Set.of("risk", "advice"), "wealth-advice");
    private static final CollaborationSignature LOAN =
            new CollaborationSignature(Set.of("loan", "credit"), "loan-intake");

    private static ExperienceMemoryKit kit(ExperienceStore store, String tenant) {
        return ExperienceMemoryKit.forTenant(store, new DefaultPiiRedactor(), tenant, () -> 1_000L);
    }

    @Test
    void recordStripsPiiBeforePersisting() {
        InMemoryExperienceStore store = new InMemoryExperienceStore();
        kit(store, "t1").record(WEALTH, List.of("client 13800138000 wanted gold funds"), "advice-agent");

        List<Lesson> hits = store.recall("t1", WEALTH, 5);
        assertEquals(1, hits.size());
        assertFalse(hits.get(0).text().contains("13800138000"), "PII stripped");
        assertTrue(hits.get(0).text().contains(DefaultPiiRedactor.MARK));
    }

    @Test
    void recallsBySignatureRelevance() {
        InMemoryExperienceStore store = new InMemoryExperienceStore();
        ExperienceMemoryKit exp = kit(store, "t1");
        exp.record(WEALTH, List.of("lead with risk rating before product pitch"), "risk-agent");
        exp.record(LOAN, List.of("pull credit before pricing"), "loan-agent");

        List<Lesson> wealthHits = exp.recall(WEALTH, 5);
        assertEquals(1, wealthHits.size(), "only the matching-signature lesson is recalled");
        assertTrue(wealthHits.get(0).text().contains("risk rating"));
    }

    @Test
    void unrelatedSignatureRecallsNothing() {
        InMemoryExperienceStore store = new InMemoryExperienceStore();
        kit(store, "t1").record(WEALTH, List.of("lesson"), "a");
        // a totally different shape (no capability overlap, different type) → no relevance
        assertTrue(kit(store, "t1").recall(LOAN, 5).isEmpty());
    }

    @Test
    void experienceIsTenantIsolated() {
        InMemoryExperienceStore store = new InMemoryExperienceStore();
        kit(store, "tenant-a").record(WEALTH, List.of("a-only lesson"), "a");
        assertTrue(kit(store, "tenant-b").recall(WEALTH, 5).isEmpty(), "tenant B sees nothing of tenant A");
    }
}
