/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.a2a;

import com.huawei.ascend.examples.travel.taskcollector.LlmConfig;
import com.huawei.ascend.examples.travel.taskcollector.TaskCollectorAgent;
import com.huawei.ascend.examples.travel.taskcollector.TaskCollectorAgentConstants;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TaskCollectorAgentConfiguration {

    @Bean
    Checkpointer taskCollectorCheckpointer() {
        return OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
    }

    @Bean
    LlmConfig taskCollectorLlmConfig(
            @Value("${task-collector-agent.llm.model-provider}") String provider,
            @Value("${task-collector-agent.llm.api-key}") String apiKey,
            @Value("${task-collector-agent.llm.api-base}") String apiBase,
            @Value("${task-collector-agent.llm.model-name}") String modelName,
            @Value("${task-collector-agent.llm.ssl-verify}") boolean sslVerify) {
        return new LlmConfig(provider, apiKey, apiBase, modelName, sslVerify);
    }

    @Bean(destroyMethod = "close")
    TaskCollectorAgent taskCollectorAgent(
            LlmConfig llmConfig,
            @Value("${task-collector-agent.max-iterations:"
                    + TaskCollectorAgentConstants.DEFAULT_MAX_ITERATIONS + "}") int maxIterations,
            @Value("${task-collector-agent.default-user-id:zhang3}") String userId,
            @Value("${task-collector-agent.default-city:深圳}") String defaultCity) {
        return new TaskCollectorAgent(llmConfig, maxIterations, userId, defaultCity);
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler taskCollectorAgentHandler(TaskCollectorAgent agent) {
        return new TaskCollectorAgentHandler(TaskCollectorAgentA2aConstants.AGENT_ID, agent);
    }

    @Bean
    AgentCard taskCollectorAgentCard() {
        return AgentCard.builder()
                .name(TaskCollectorAgentA2aConstants.AGENT_ID)
                .description("Corporate-travel task collector sub-agent. It collects calendar events, "
                        + "todos and travel policies through MCP tools, and returns a structured "
                        + "markdown checklist for the trip planning agent.")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", ""))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("travel-task-collection")
                                .name("Collect corporate travel tasks")
                                .description("Collect calendar events, todos, travel policies and "
                                        + "constraints for a business trip.")
                                .tags(List.of("travel", "corporate-travel", "task-collection", "mcp"))
                                .examples(List.of(
                                        "帮我收集 2026-06-18 到 2026-06-20 北京出差相关的会议、待办和差标。"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build()))
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }

    static final class TaskCollectorAgentHandler extends OpenJiuwenAgentRuntimeHandler {

        private final TaskCollectorAgent agent;

        TaskCollectorAgentHandler(String agentId, TaskCollectorAgent agent) {
            super(agentId);
            this.agent = agent;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return agent.newBaseAgent();
        }
    }
}
