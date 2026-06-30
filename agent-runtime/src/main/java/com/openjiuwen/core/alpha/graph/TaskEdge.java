package com.openjiuwen.core.alpha.graph;

import com.openjiuwen.core.kernel.model.NodeId;

/**
 * 任务图边——节点间的依赖关系。
 *
 * from 节点完成后，其输出通过 dataRef 传递给 to 节点。
 * dataRef 格式示例：from 节点的完整输出 = "output"
 *                   from 节点的某个字段 = "result.orderId"
 */
public record TaskEdge(
    NodeId from,
    NodeId to,
    String dataRef
) {

    public TaskEdge {
        if (from == null) throw new IllegalArgumentException("from 不能为 null");
        if (to == null) throw new IllegalArgumentException("to 不能为 null");
        if (dataRef == null || dataRef.isBlank()) dataRef = "output";
    }

    /** 便捷构造：默认 dataRef = "output" */
    public static TaskEdge of(String from, String to) {
        return new TaskEdge(new NodeId(from), new NodeId(to), "output");
    }

    /** 便捷构造：指定数据引用 */
    public static TaskEdge of(String from, String to, String dataRef) {
        return new TaskEdge(new NodeId(from), new NodeId(to), dataRef);
    }
}
