package com.huawei.ascend.a2a.memory.hook;

import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;

/**
 * The run-end integration seam (the a2a-shared-memory design decision): the collaboration coordinator calls
 * this when a collaboration finishes, so A2A shared-memory can distill the run's blackboard
 * into cross-run experience. The dependency points <b>collaboration &rarr;
 * A2A shared-memory</b> (A2A shared-memory never imports the collaboration module), which is what keeps
 * A2A shared-memory an independent engine that builds on its own.
 */
public interface CollaborationMemoryHook {

    /**
     * Called once a collaboration ends. Implementations distill the blackboard
     * into experience (PII-stripped) under {@code signature}, and may release the
     * blackboard.
     *
     * @param signature  capability-set + task-type of the finished collaboration
     * @param blackboard the run's shared blackboard (read its keys to distill)
     */
    void onCollaborationEnd(CollaborationSignature signature, SharedMemoryKit blackboard);

    /** No-op hook (memory integration disabled). */
    CollaborationMemoryHook NOOP = (signature, blackboard) -> {
    };
}
