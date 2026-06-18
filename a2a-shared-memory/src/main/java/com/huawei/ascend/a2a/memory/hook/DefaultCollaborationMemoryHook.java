package com.huawei.ascend.a2a.memory.hook;

import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceMemoryKit;
import com.huawei.ascend.a2a.memory.shared.SharedEntry;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link CollaborationMemoryHook}: at run end, turn each blackboard key's
 * latest entry into a raw lesson ({@code "key: value (by agent)"}) and record it
 * into the experience layer — which redacts PII before persisting. Optionally
 * releases the blackboard afterwards.
 *
 * <p>Phase-1 distillation is intentionally simple (latest value per key). A
 * smarter summariser can replace it without changing the seam.
 */
public final class DefaultCollaborationMemoryHook implements CollaborationMemoryHook {

    private final ExperienceMemoryKit experience;
    private final boolean releaseBlackboard;

    public DefaultCollaborationMemoryHook(ExperienceMemoryKit experience, boolean releaseBlackboard) {
        this.experience = experience;
        this.releaseBlackboard = releaseBlackboard;
    }

    @Override
    public void onCollaborationEnd(CollaborationSignature signature, SharedMemoryKit blackboard) {
        List<String> lessons = new ArrayList<>();
        String sourceAgentId = null;
        for (String key : blackboard.keys()) {
            SharedEntry entry = blackboard.entry(key).orElse(null);
            if (entry == null || entry.value() == null || entry.value().isBlank()) {
                continue;
            }
            lessons.add(key + ": " + entry.value() + " (by " + entry.writerAgentId() + ")");
            if (sourceAgentId == null) {
                sourceAgentId = entry.writerAgentId();
            }
        }
        if (!lessons.isEmpty()) {
            experience.record(signature, lessons, sourceAgentId);
        }
        if (releaseBlackboard) {
            blackboard.release();
        }
    }
}
