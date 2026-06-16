package com.huawei.ascend.a2a.memory.experience;

import java.util.Set;
import java.util.TreeSet;

/**
 * What a collaboration "looks like", used to index and recall cross-run
 * experience (the a2a-shared-memory design decision: signature = capability-set + task-type). Recall ranks
 * stored experience by {@link #similarity(CollaborationSignature)} so a new
 * collaboration retrieves lessons from past collaborations of the same shape.
 *
 * @param capabilities the set of capabilities the collaboration involved
 * @param taskType     a coarse task-type label (e.g. "wealth-advice", "loan-intake")
 */
public record CollaborationSignature(Set<String> capabilities, String taskType) {

    public CollaborationSignature {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(new TreeSet<>(capabilities));
        taskType = taskType == null ? "" : taskType;
    }

    /**
     * Similarity in [0,1]: Jaccard overlap of capability sets, plus a fixed bonus
     * when the task types match, normalised. 1.0 = same capabilities and task type.
     */
    public double similarity(CollaborationSignature other) {
        if (other == null) {
            return 0.0;
        }
        double jaccard = jaccard(capabilities, other.capabilities);
        double typeMatch = !taskType.isBlank() && taskType.equals(other.taskType) ? 1.0 : 0.0;
        // Weight capabilities and task type equally.
        return (jaccard + typeMatch) / 2.0;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        long inter = a.stream().filter(b::contains).count();
        long union = a.size() + b.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }
}
