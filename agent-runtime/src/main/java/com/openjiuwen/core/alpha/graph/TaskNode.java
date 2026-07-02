package com.openjiuwen.core.alpha.graph;

import com.openjiuwen.core.kernel.model.NodeId;

import java.util.Map;

/**
 * 任务图节点——一个可执行的子任务。
 *
 * 节点类型：
 * - TOOL_CALL:  调用一个已注册的工具（确定性执行）
 * - LLM_CALL:   调用 LLM 进行推理（需要思考）
 * - SUB_AGENT:  递归执行一个新的 Plan-Execute-Verify（复杂子任务）
 *
 * inputs 中可以引用上游节点的输出，格式：${nodeId.output}
 * 执行时由 AlphaStrategy 解析替换。
 *
 * <p>correctionHint：可选的校验纠错提示。仅 LocalReplan（验证失败后局部重做）由
 * AlphaStrategy 注入地面真值反馈；执行器只在 LLM_CALL 节点的 prompt 中以 {@code <correction>}
 * 标签消费它，让 LLM 据上次校验失败的原因自纠。TOOL_CALL / SUB_AGENT 不读它。默认 null。
 */
public record TaskNode(
    NodeId id,
    String description,
    TaskNodeType type,
    Map<String, String> inputs,
    String expectedOutput,
    TaskNodeStatus status,
    String correctionHint
) {

    public TaskNode {
        if (id == null) throw new IllegalArgumentException("节点 ID 不能为 null");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("节点描述不能为空");
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }

    /**
     * 向后兼容构造器——不携带纠错提示（correctionHint=null）。
     *
     * <p>保留 6 参签名，使既有所有 {@code new TaskNode(...)} 调用点（工厂 {@link #of}、
     * {@code DefaultPlanner.parseNode}、{@link #withStatus}）零改动继续编译；仅 LocalReplan
     * 重做路径需显式注入 correctionHint 时走 7 参 canonical。
     */
    public TaskNode(NodeId id, String description, TaskNodeType type,
                    Map<String, String> inputs, String expectedOutput, TaskNodeStatus status) {
        this(id, description, type, inputs, expectedOutput, status, null);
    }

    /** 便捷构造：默认 PENDING 状态 */
    public static TaskNode of(String id, String description, TaskNodeType type) {
        return new TaskNode(new NodeId(id), description, type, Map.of(), null, TaskNodeStatus.PENDING);
    }

    /** 便捷构造：带输入引用 */
    public static TaskNode of(String id, String description, TaskNodeType type, Map<String, String> inputs) {
        return new TaskNode(new NodeId(id), description, type, inputs, null, TaskNodeStatus.PENDING);
    }

    /** 更新节点状态 */
    public TaskNode withStatus(TaskNodeStatus newStatus) {
        return new TaskNode(id, description, type, inputs, expectedOutput, newStatus);
    }
}
