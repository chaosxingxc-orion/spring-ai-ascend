package com.openjiuwen.core.alpha.executor;

import com.openjiuwen.core.kernel.model.NodeId;

import java.util.Map;
import java.util.Set;

/**
 * 超步结果——Pregel BSP 模型中一个超步的执行结果。
 *
 * <p>一个超步 = 同一层所有节点的并行执行。超步之间有同步屏障（barrier）。
 */
public record SuperstepResult(
    int superstepIndex,
    Map<NodeId, Object> nodeResults,
    Set<NodeId> failedNodes,
    Set<NodeId> skippedNodes
) {
    public boolean hasFailures() {
        return failedNodes != null && !failedNodes.isEmpty();
    }

    public boolean allSucceeded() {
        return !hasFailures() && (skippedNodes == null || skippedNodes.isEmpty());
    }
}
