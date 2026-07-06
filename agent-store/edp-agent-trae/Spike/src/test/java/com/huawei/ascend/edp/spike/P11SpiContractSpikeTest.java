package com.huawei.ascend.edp.spike;

import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.handler.EdpaRuntimeHandler;
import com.huawei.ascend.edp.enhancer.EdpaAgentEnhancer;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P11: OpenJiuwenAgentRuntimeHandler SPI 契约验证")
class P11SpiContractSpikeTest {

    @Test
    @DisplayName("P11-1: EdpaRuntimeHandler 继承 OpenJiuwenAgentRuntimeHandler")
    void handlerExtendsCorrectBaseClass() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        assertInstanceOf(com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler.class,
                handler, "EdpaRuntimeHandler must extend OpenJiuwenAgentRuntimeHandler");
    }

    @Test
    @DisplayName("P11-2: agentId() 返回 'edp-agent'")
    void agentIdReturnsCorrectValue() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        assertEquals("edp-agent", handler.agentId(), "agentId must be 'edp-agent'");
    }

    @Test
    @DisplayName("P11-3: isHealthy() 在 init 前返回 false")
    void isHealthyReturnsFalseBeforeInit() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        assertFalse(handler.isHealthy(), "isHealthy must be false before init");
    }

    @Test
    @DisplayName("P11-4: init() 后 isHealthy() 返回 true")
    void isHealthyReturnsTrueAfterInit() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        String yamlPath = Path.of("src/main/resources/edp-agent.yaml").toString();
        String configPath = Path.of("src/main/resources/edp-config.yaml").toString();

        assertDoesNotThrow(() -> handler.init(yamlPath, configPath));
        assertTrue(handler.isHealthy(), "isHealthy must be true after init");
    }

    @Test
    @DisplayName("P11-5: DeepAgent.getAgent() 返回 BaseAgent 实例（createOpenJiuwenAgent 间接验证）")
    void deepAgentGetAgentReturnsBaseAgent() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        handler.init(
                Path.of("src/main/resources/edp-agent.yaml").toString(),
                Path.of("src/main/resources/edp-config.yaml").toString());

        BaseAgent agent = handler.getDeepAgent().getAgent();
        assertNotNull(agent, "DeepAgent.getAgent() must return non-null BaseAgent");
        assertInstanceOf(BaseAgent.class, agent, "Returned agent must be BaseAgent instance");
    }

    @Test
    @DisplayName("P11-6: openJiuwenRails 返回 EDPAgent 专有 Rail 列表（通过反射验证 protected 方法）")
    void openJiuwenRailsReturnsBusinessRails() throws Exception {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        handler.init(
                Path.of("src/main/resources/edp-agent.yaml").toString(),
                Path.of("src/main/resources/edp-config.yaml").toString());

        AgentExecutionContext ctx = buildMinimalContext();
        java.lang.reflect.Method railsMethod =
                com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler.class
                        .getDeclaredMethod("openJiuwenRails", AgentExecutionContext.class);
        railsMethod.setAccessible(true);
        List<AgentRail> rails = (List<AgentRail>) railsMethod.invoke(handler, ctx);

        assertNotNull(rails, "openJiuwenRails must return non-null list");
        assertEquals(0, rails.size(), "openJiuwenRails returns empty list because business rails are registered during init");
    }

    private AgentExecutionContext buildMinimalContext() {
        RuntimeIdentity scope = new RuntimeIdentity(
                "test-tenant", "test-session", "test-task", "edp-agent", "test-user");
        return new AgentExecutionContext(scope, "USER_MESSAGE",
                List.of(RuntimeMessage.user("请推荐几款理财产品")),
                Map.of());
    }
}
