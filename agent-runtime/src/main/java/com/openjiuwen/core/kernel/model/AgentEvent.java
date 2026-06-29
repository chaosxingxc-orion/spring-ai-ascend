package com.openjiuwen.core.kernel.model;

import java.time.Instant;
import java.util.Map;

/**
 * Agent 事件基类。
 * 统一的事件模型：不管底层用 Alpha（PEV）还是 Beta（LLM 自主），事件格式一致。
 *
 * 契约：
 * - taskId 必须存在，用于事件溯源和日志追踪
 * - type 决定了 data 的结构
 * - timestamp 单调递增，用于排序
 * - metadata 携带策略专属信息（Alpha: planId/nodeId, Beta: decisionType）
 */
public record AgentEvent(
    TaskId taskId,
    EventType type,
    String data,
    Map<String, String> metadata,
    Instant timestamp
) {

    /** 创建带 taskId 和 type 的事件，自动填充时间戳 */
    public static AgentEvent of(TaskId taskId, EventType type, String data) {
        return new AgentEvent(taskId, type, data, Map.of(), Instant.now());
    }

    /** 创建带元数据的事件 */
    public static AgentEvent of(TaskId taskId, EventType type, String data, Map<String, String> metadata) {
        return new AgentEvent(taskId, type, data, metadata, Instant.now());
    }
}
