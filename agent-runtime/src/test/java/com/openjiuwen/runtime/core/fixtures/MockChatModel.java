package com.openjiuwen.runtime.core.fixtures;

import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;

import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock LLM Provider -- returns predefined responses for testing.
 *
 * Supports two modes:
 * 1. Fixed response: always returns the same string
 * 2. Pattern-based: matches prompt keywords and returns corresponding responses
 *
 * 暴露 {@link #callCount()} 以便测试钉死「某路径是否真触达 LLM」（如 NONE 短路零调用）。
 */
public class MockChatModel implements DefaultAgentKernel.LLMProvider {

    private String defaultResponse;
    private Function<String, String> responseFn;
    private final AtomicInteger callCount = new AtomicInteger();

    /** Fixed response mode */
    public MockChatModel(String response) {
        this.defaultResponse = response;
    }

    /** Dynamic response mode */
    public MockChatModel(Function<String, String> responseFn) {
        this.responseFn = responseFn;
    }

    @Override
    public String call(String prompt) {
        callCount.incrementAndGet();
        if (responseFn != null) {
            return responseFn.apply(prompt);
        }
        return defaultResponse;
    }

    /** 本 mock 被 LLM 调用的次数（用于断言某路径是否真触达 think）。 */
    public int callCount() {
        return callCount.get();
    }

    // ==================== Predefined JSON responses ====================

    /** A valid 3-node linear DAG: A -> B -> C */
    public static final String LINEAR_3_NODE_JSON = """
        ```json
        {
          "nodes": [
            {"id": "A", "description": "查询数据", "type": "TOOL_CALL"},
            {"id": "B", "description": "分析结果", "type": "LLM_CALL"},
            {"id": "C", "description": "生成报告", "type": "LLM_CALL"}
          ],
          "edges": [
            {"from": "A", "to": "B"},
            {"from": "B", "to": "C"}
          ]
        }
        ```
        """;

    /** A valid 3-node parallel DAG: A and B independent, both -> C */
    public static final String PARALLEL_3_NODE_JSON = """
        ```json
        {
          "nodes": [
            {"id": "A", "description": "查询订单", "type": "TOOL_CALL"},
            {"id": "B", "description": "查询库存", "type": "TOOL_CALL"},
            {"id": "C", "description": "汇总分析", "type": "LLM_CALL"}
          ],
          "edges": [
            {"from": "A", "to": "C"},
            {"from": "B", "to": "C"}
          ]
        }
        ```
        """;

    /** A cyclic graph: A -> B -> C -> A */
    public static final String CYCLIC_JSON = """
        ```json
        {
          "nodes": [
            {"id": "A", "description": "步骤A", "type": "LLM_CALL"},
            {"id": "B", "description": "步骤B", "type": "LLM_CALL"},
            {"id": "C", "description": "步骤C", "type": "LLM_CALL"}
          ],
          "edges": [
            {"from": "A", "to": "B"},
            {"from": "B", "to": "C"},
            {"from": "C", "to": "A"}
          ]
        }
        ```
        """;

    /** Verify pass response */
    public static final String VERIFY_PASS = "PASS: 所有节点输出正确，满足目标要求";

    /** Verify fail response */
    public static final String VERIFY_FAIL = "FAIL: 节点B输出不满足要求 [B]";
}
