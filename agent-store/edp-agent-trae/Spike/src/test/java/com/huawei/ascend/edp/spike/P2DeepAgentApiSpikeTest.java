package com.huawei.ascend.edp.spike;

import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.EdpConfigLoader;
import com.huawei.ascend.edp.enhancer.EdpaAgentEnhancer;
import com.huawei.ascend.edp.rail.CancelRail;
import com.huawei.ascend.edp.rail.LiteTodoRail;
import com.huawei.ascend.edp.rail.ExecutionLimitRail;
import com.huawei.ascend.edp.rail.VersatileInterruptRail;
import com.huawei.ascend.edp.rail.McpInterruptRail;
import com.huawei.ascend.edp.rail.AskUserTemplateRail;
import com.huawei.ascend.edp.rail.LogRail;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P2: DeepAgent API 验证")
class P2DeepAgentApiSpikeTest {

    @Test
    @DisplayName("P2-1: AgentFactory.toDeepAgent 创建 DeepAgent 不抛异常")
    void deepAgentCreationNoException() {
        DeepAgentConfig config = DeepAgentConfig.builder().build();
        assertDoesNotThrow(() -> {
            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            assertNotNull(agent, "DeepAgent instance must not be null");
        });
    }

    @Test
    @DisplayName("P2-2: agent.registerRail 注册自定义 Rail")
    void registerCustomRailOnDeepAgent() {
        DeepAgentConfig config = DeepAgentConfig.builder().build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);

        AgentRail testRail = new CancelRail(new EdpConfig());
        assertDoesNotThrow(() -> {
            agent.getAgent().registerRail(testRail);
        });
    }

    @Test
    @DisplayName("P2-3: 多个自定义 Rail 同时注册不冲突")
    void registerMultipleRailsNoConflict() {
        DeepAgentConfig config = DeepAgentConfig.builder().build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);

        EdpConfig edpConfig = new EdpConfig();
        List<AgentRail> rails = EdpaAgentEnhancer.buildBusinessRails(edpConfig);

        assertDoesNotThrow(() -> {
            for (AgentRail rail : rails) {
                agent.getAgent().registerRail(rail);
            }
        });

        assertEquals(7, rails.size(), "Should have 7 business rails");
    }

    @Test
    @DisplayName("P2-4: Rail 优先级设置正确")
    void railPrioritySetCorrectly() {
        EdpConfig edpConfig = new EdpConfig();
        List<AgentRail> rails = EdpaAgentEnhancer.buildBusinessRails(edpConfig);

        assertEquals(10, rails.get(0).getPriority(), "CancelRail priority=10");
        assertEquals(35, rails.get(1).getPriority(), "LiteTodoRail priority=35");
        assertEquals(40, rails.get(2).getPriority(), "ExecutionLimitRail priority=40");
        assertEquals(50, rails.get(3).getPriority(), "McpInterruptRail priority=50");
        assertEquals(50, rails.get(4).getPriority(), "VersatileInterruptRail priority=50");
        assertEquals(50, rails.get(5).getPriority(), "AskUserTemplateRail priority=50");
        assertEquals(1000, rails.get(6).getPriority(), "LogRail priority=1000");
    }

    @Test
    @DisplayName("P2-5: EdpaAgentEnhancer.enhance 注册全部业务 Rail")
    void enhanceRegistersAllBusinessRails() {
        DeepAgentConfig config = DeepAgentConfig.builder().build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        EdpConfig edpConfig = new EdpConfig();

        assertDoesNotThrow(() -> {
            EdpaAgentEnhancer.enhance(agent, edpConfig);
        });
    }

    @Test
    @DisplayName("P2-6: DeepAgent IS-A BaseAgent 验证（P14 关键前置）")
    void deepAgentIsBaseAgent() {
        DeepAgentConfig config = DeepAgentConfig.builder().build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);

        assertNotNull(agent.getAgent(), "DeepAgent.getAgent() must return ReActAgent instance");
        assertInstanceOf(com.openjiuwen.core.singleagent.BaseAgent.class, agent.getAgent(),
                "DeepAgent.getAgent() must return BaseAgent instance (ReActAgent extends BaseAgent)");
    }
}
