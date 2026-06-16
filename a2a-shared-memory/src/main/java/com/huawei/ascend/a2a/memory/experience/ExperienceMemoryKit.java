package com.huawei.ascend.a2a.memory.experience;

import com.huawei.ascend.a2a.memory.privacy.DefaultPiiRedactor;
import com.huawei.ascend.a2a.memory.privacy.PiiRedactor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Kit facade for cross-run collaboration experience (the a2a-shared-memory design decision). Bound to one
 * tenant. {@link #record} <b>always redacts PII</b> before persisting — the
 * locked privacy guard for the tenant-shared experience layer. {@link #recall}
 * returns the most relevant past lessons for a signature.
 *
 * <pre>{@code
 * ExperienceMemoryKit exp = ExperienceMemoryKit.forTenant(store, "demo-tenant");
 * exp.record(signature, List.of("loan agent should pull credit before pricing"), "loan-agent");
 * exp.recall(signature, 5);
 * }</pre>
 */
public final class ExperienceMemoryKit {

    private final ExperienceStore store;
    private final PiiRedactor redactor;
    private final String tenantId;
    private final LongSupplier clock;

    private ExperienceMemoryKit(ExperienceStore store, PiiRedactor redactor, String tenantId, LongSupplier clock) {
        this.store = store;
        this.redactor = redactor == null ? new DefaultPiiRedactor() : redactor;
        this.tenantId = tenantId;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Default redactor ({@link DefaultPiiRedactor}) and system clock. */
    public static ExperienceMemoryKit forTenant(ExperienceStore store, String tenantId) {
        return new ExperienceMemoryKit(store, new DefaultPiiRedactor(), tenantId, System::currentTimeMillis);
    }

    public static ExperienceMemoryKit forTenant(ExperienceStore store, PiiRedactor redactor, String tenantId,
            LongSupplier clock) {
        return new ExperienceMemoryKit(store, redactor, tenantId, clock);
    }

    /** Distill raw lessons into the experience layer, PII-stripped, under a signature. */
    public void record(CollaborationSignature signature, List<String> rawLessons, String sourceAgentId) {
        if (rawLessons == null || rawLessons.isEmpty()) {
            return;
        }
        long ts = clock.getAsLong();
        List<Lesson> lessons = new ArrayList<>();
        for (String raw : rawLessons) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            lessons.add(new Lesson(redactor.redact(raw), sourceAgentId, ts));
        }
        store.record(tenantId, signature, lessons);
    }

    /** Recall the most relevant lessons for a signature (freshest / most-useful first). */
    public List<Lesson> recall(CollaborationSignature signature, int topK) {
        return store.recall(tenantId, signature, topK);
    }

    /** Usefulness feedback: a recalled lesson proved useful — reinforce it (keeps memory fresh). */
    public void reinforce(CollaborationSignature signature, String lessonText) {
        store.reinforce(tenantId, signature, lessonText);
    }

    public String tenantId() {
        return tenantId;
    }
}
