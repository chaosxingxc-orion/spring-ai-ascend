package com.huawei.ascend.edp.enhancer;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenStreamAdapter;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * EDPAgent 事件流适配器 -- 将 EdpaEventRail 发射的 custom OutputSchema 转为 A2A SSE 可消费的
 * AgentExecutionResult 帧, 与主流程 Handler 解耦。
 *
 * <p>职责单一:</p>
 * <ul>
 *     <li>custom OutputSchema -> AgentExecutionResult.output(eventJson, Target.USER)
 *         -- 前端按 a2a.target=USER + payload.event 路由消费</li>
 *     <li>其余 OutputSchema 委托给 OpenJiuwenStreamAdapter 默认映射</li>
 *     <li>null / AgentExecutionResult / 其他类型走兜底逻辑</li>
 * </ul>
 *
 * <p>使用: EdpaRuntimeHandler.resultAdapter() 直接 return 本实例即可。</p>
 */
public class EdpaEventStreamAdapter implements StreamAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpaEventStreamAdapter.class);

    private static final OpenJiuwenStreamAdapter DELEGATE = new OpenJiuwenStreamAdapter();

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public java.util.stream.Stream<AgentExecutionResult> adapt(java.util.stream.Stream<?> rawResults) {
        return rawResults.map(this::mapResult).filter(Objects::nonNull);
    }

    @SuppressWarnings("unchecked")
    private AgentExecutionResult mapResult(Object rawResult) {
        if (rawResult instanceof OutputSchema) {
            OutputSchema chunk = (OutputSchema) rawResult;
            if ("custom".equals(chunk.getType())) {
                Object payload = chunk.getPayload();
                if (payload instanceof Map) {
                    return AgentExecutionResult.output(toJson((Map<String, Object>) payload),
                            AgentExecutionResult.Target.USER);
                }
                return AgentExecutionResult.output(String.valueOf(payload), AgentExecutionResult.Target.USER);
            }
            // 抑制 llm_output：LLM 流式内容已由 EdpaEventRail 在 afterModelCall 中
            // 通过 think_chunk / final_answer_chunk 发射，此处再转发会导致内容重复（裸流文本 + 事件）。
            if ("llm_output".equals(chunk.getType())) {
                return null;
            }
            return DELEGATE.map(chunk);
        }
        if (rawResult instanceof AgentExecutionResult) {
            return (AgentExecutionResult) rawResult;
        }
        if (rawResult == null) {
            return AgentExecutionResult.failed("OPENJIUWEN_ERROR", "openjiuwen runner returned no result");
        }
        return AgentExecutionResult.output(String.valueOf(rawResult));
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            LOGGER.warn("EdpaEventStreamAdapter JSON serialize failed: {}", e.getMessage());
            return String.valueOf(map);
        }
    }
}
