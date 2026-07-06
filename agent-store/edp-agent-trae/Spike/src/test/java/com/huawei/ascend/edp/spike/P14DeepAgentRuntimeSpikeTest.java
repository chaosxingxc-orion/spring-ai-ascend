package com.huawei.ascend.edp.spike;

import com.huawei.ascend.edp.handler.EdpaRuntimeHandler;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenStreamAdapter;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P14: OpenJiuwenAgentRuntimeHandler + DeepAgent 适配验证")
class P14DeepAgentRuntimeSpikeTest {

    @Test
    @DisplayName("P14-1: DeepAgent IS-A BaseAgent（关键不确定性验证）")
    void deepAgentIsBaseAgent() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        handler.init(
                Path.of("src/main/resources/edp-agent.yaml").toString(),
                Path.of("src/main/resources/edp-config.yaml").toString());

        BaseAgent agent = handler.getDeepAgent().getAgent();
        assertNotNull(agent, "DeepAgent.getAgent() must return non-null");
        assertInstanceOf(BaseAgent.class, agent,
                "DeepAgent.getAgent() returns ReActAgent which extends BaseAgent — P14 KEY UNCERTAINTY RESOLVED");
    }

    @Test
    @DisplayName("P14-2: createOpenJiuwenAgent 返回 BaseAgent（通过反射验证 protected 方法）")
    void createOpenJiuwenAgentReturnsBaseAgent() throws Exception {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        handler.init(
                Path.of("src/main/resources/edp-agent.yaml").toString(),
                Path.of("src/main/resources/edp-config.yaml").toString());

        AgentExecutionContext ctx = buildMinimalContext();
        java.lang.reflect.Method createMethod =
                com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler.class
                        .getDeclaredMethod("createOpenJiuwenAgent", AgentExecutionContext.class);
        createMethod.setAccessible(true);
        BaseAgent agent = (BaseAgent) createMethod.invoke(handler, ctx);

        assertNotNull(agent, "createOpenJiuwenAgent must return non-null BaseAgent");
        assertInstanceOf(BaseAgent.class, agent, "Returned agent must be BaseAgent instance");
    }

    @Test
    @DisplayName("P14-3: OpenJiuwenStreamAdapter 适配 DeepAgent OutputSchema")
    void streamAdapterAdaptsDeepAgentOutputSchema() {
        OpenJiuwenStreamAdapter adapter = new OpenJiuwenStreamAdapter();

        OutputSchema answerChunk = new OutputSchema("answer", 0, "推荐结果");
        AgentExecutionResult result = adapter.map(answerChunk);
        assertNotNull(result, "OpenJiuwenStreamAdapter must map answer chunk");
        assertEquals(AgentExecutionResult.Type.COMPLETED,
                result.type(), "answer chunk must map to COMPLETED");
    }

    private AgentExecutionContext buildMinimalContext() {
        RuntimeIdentity scope = new RuntimeIdentity(
                "test-tenant", "test-session", "test-task", "edp-agent", "test-user");
        return new AgentExecutionContext(scope, "USER_MESSAGE",
                List.of(RuntimeMessage.user("请推荐几款理财产品")),
                Map.of());
    }
}
