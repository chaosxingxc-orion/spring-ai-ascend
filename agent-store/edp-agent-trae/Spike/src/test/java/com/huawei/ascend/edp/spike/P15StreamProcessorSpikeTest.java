package com.huawei.ascend.edp.spike;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenStreamAdapter;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P15: _StreamProcessor 17 种事件状态机迁移验证")
class P15StreamProcessorSpikeTest {

    private final OpenJiuwenStreamAdapter adapter = new OpenJiuwenStreamAdapter();

    @Test
    @DisplayName("P15-1: llm_output 事件映射为 OUTPUT")
    void llmOutputMapsToOutput() {
        OutputSchema chunk = new OutputSchema("llm_output", 0,
                Map.of("content", "正在分析您的需求..."));

        AgentExecutionResult result = adapter.map(chunk);
        assertNotNull(result);
        assertEquals(AgentExecutionResult.Type.OUTPUT, result.type());
        assertEquals("正在分析您的需求...", result.outputContent());
    }

    @Test
    @DisplayName("P15-2: answer 事件映射为 COMPLETED")
    void answerMapsToCompleted() {
        OutputSchema chunk = new OutputSchema("answer", 0, "推荐结果：以下是3款理财产品...");

        AgentExecutionResult result = adapter.map(chunk);
        assertNotNull(result);
        assertEquals(AgentExecutionResult.Type.COMPLETED, result.type());
        assertEquals("推荐结果：以下是3款理财产品...", result.outputContent());
    }

    @Test
    @DisplayName("P15-3: __interaction__ 事件映射为 INTERRUPTED（用户输入中断）")
    void interactionMapsToInterruptedUserInput() {
        InterruptRequest request = InterruptRequest.builder()
                .interruptId("ask_user_interrupt_001")
                .message("请确认您的购买金额")
                .build();

        InteractionOutput interactionOutput = new InteractionOutput(
                "ask_user_interrupt_001", request);

        OutputSchema chunk = new OutputSchema("__interaction__", 0, interactionOutput);

        AgentExecutionResult result = adapter.map(chunk);
        assertNotNull(result);
        assertEquals(AgentExecutionResult.Type.INTERRUPTED, result.type());
        assertNotNull(result.prompt(), "User input interrupt must have prompt");
    }

    @Test
    @DisplayName("P15-4: __interaction__ 事件映射为 INTERRUPTED（远程调用中断）")
    void interactionMapsToInterruptedRemoteInvocation() {
        LinkedHashMap<String, Object> remoteContext = new LinkedHashMap<>();
        remoteContext.put("runtime.remote.kind", "REMOTE_AGENT_INVOCATION");
        remoteContext.put("runtime.remote.agentId", "versatile_agent");
        remoteContext.put("runtime.remote.toolName", "call_versatile");
        remoteContext.put("runtime.remote.toolCallId", "tc_001");
        remoteContext.put("runtime.remote.parentTaskId", "task_001");
        remoteContext.put("runtime.remote.parentContextId", "ctx_001");
        remoteContext.put("runtime.remote.localConversationId", "conv_001");

        InterruptRequest request = InterruptRequest.builder()
                .interruptId("versatile_interrupt_001")
                .message("需要委托 Versatile 执行")
                .context(remoteContext)
                .build();

        InteractionOutput interactionOutput = new InteractionOutput(
                "versatile_interrupt_001", request);

        OutputSchema chunk = new OutputSchema("__interaction__", 0, interactionOutput);

        AgentExecutionResult result = adapter.map(chunk);
        assertNotNull(result);
        assertEquals(AgentExecutionResult.Type.INTERRUPTED, result.type());
        assertNotNull(result.remoteInvocation(), "Remote invocation must have RemoteInvocation payload");
        assertEquals("versatile_agent", result.remoteInvocation().remoteAgentId());
    }

    @Test
    @DisplayName("P15-5: llm_usage / llm_reasoning / custom 事件映射为 null（过滤）")
    void usageReasoningCustomFilteredAsNull() {
        OutputSchema usageChunk = new OutputSchema("llm_usage", 0, Map.of("tokens", 100));
        assertNull(adapter.map(usageChunk), "llm_usage must be filtered to null");

        OutputSchema reasoningChunk = new OutputSchema("llm_reasoning", 0, "思考过程...");
        assertNull(adapter.map(reasoningChunk), "llm_reasoning must be filtered to null");

        OutputSchema customChunk = new OutputSchema("custom", 0, Map.of("event", "todo_update"));
        assertNull(adapter.map(customChunk), "custom must be filtered to null");
    }

    @Test
    @DisplayName("P15-6: null chunk 映射为 null")
    void nullChunkMapsToNull() {
        assertNull(adapter.map(null), "null chunk must map to null");
    }

    @Test
    @DisplayName("P15-7: llm_output 空 content 映射为 null")
    void emptyLlmOutputMapsToNull() {
        OutputSchema chunk = new OutputSchema("llm_output", 0, "");
        assertNull(adapter.map(chunk), "Empty llm_output must map to null");
    }
}
