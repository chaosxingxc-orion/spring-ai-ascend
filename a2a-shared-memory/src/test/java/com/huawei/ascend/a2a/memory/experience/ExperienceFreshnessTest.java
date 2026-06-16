package com.huawei.ascend.a2a.memory.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Experience freshness (Q3): dedup-refresh, usefulness reinforcement ranking, bounded eviction. */
class ExperienceFreshnessTest {

    private static final CollaborationSignature SIG =
            new CollaborationSignature(Set.of("risk", "loan"), "wealth-advice");

    @Test
    void reRecordingRefreshesInsteadOfDuplicating() {
        AtomicLong now = new AtomicLong(1);
        ExperienceStore store = new InMemoryExperienceStore();
        ExperienceMemoryKit exp = ExperienceMemoryKit.forTenant(store, new com.huawei.ascend.a2a.memory.privacy.DefaultPiiRedactor(),
                "bank", now::get);
        exp.record(SIG, List.of("pull credit before pricing"), "loan-agent");
        now.set(2);
        exp.record(SIG, List.of("pull credit before pricing"), "loan-agent"); // same lesson again

        List<Lesson> hits = exp.recall(SIG, 10);
        assertEquals(1, hits.size(), "re-record refreshes, not duplicates");
        assertEquals(1, hits.get(0).reinforcement(), "reinforcement bumped on re-confirm");
        assertEquals(2L, hits.get(0).tsEpochMs(), "recency refreshed");
    }

    @Test
    void reinforcedLessonsRankAboveColdOnes() {
        ExperienceStore store = new InMemoryExperienceStore();
        store.record("bank", SIG, List.of(new Lesson("cold lesson", "a", 10L)));
        store.record("bank", SIG, List.of(new Lesson("useful lesson", "a", 5L)));
        // the older "useful lesson" is reinforced twice → should rank first despite older ts
        store.reinforce("bank", SIG, "useful lesson");
        store.reinforce("bank", SIG, "useful lesson");

        List<Lesson> hits = store.recall("bank", SIG, 10);
        assertEquals("useful lesson", hits.get(0).text(), "reinforced (useful) ranks above cold");
    }

    @Test
    void boundedEvictionDropsStaleUnreinforcedLessons() {
        ExperienceStore store = new InMemoryExperienceStore(2); // cap = 2 per tenant
        store.record("bank", SIG, List.of(new Lesson("old-cold", "a", 1L)));
        store.record("bank", SIG, List.of(new Lesson("kept-useful", "a", 2L)));
        store.reinforce("bank", SIG, "kept-useful");
        store.record("bank", SIG, List.of(new Lesson("newer", "a", 3L))); // over cap → evict lowest

        List<Lesson> hits = store.recall("bank", SIG, 10);
        assertEquals(2, hits.size(), "capped at 2");
        assertTrue(hits.stream().anyMatch(l -> l.text().equals("kept-useful")), "reinforced survives");
        assertTrue(hits.stream().noneMatch(l -> l.text().equals("old-cold")), "stale unreinforced evicted");
    }
}
