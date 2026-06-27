package com.huawei.ascend.examples.workmate.member;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WorkmateMemberAgentConfiguration {

    @Bean
    OpenJiuwenAgentRuntimeHandler workmateMemberAgentHandler(
            MemberExpertPromptLoader promptLoader,
            WorkmateMemberLlmProperties llm) {
        return new WorkmateMemberAgentHandler(promptLoader, llm);
    }

    static final class WorkmateMemberAgentHandler extends OpenJiuwenAgentRuntimeHandler {

        private final MemberExpertPromptLoader promptLoader;
        private final WorkmateMemberLlmProperties llm;

        WorkmateMemberAgentHandler(MemberExpertPromptLoader promptLoader, WorkmateMemberLlmProperties llm) {
            super("workmate-member-" + promptLoader.expertId());
            this.promptLoader = promptLoader;
            this.llm = llm;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            String agentId = "workmate-member-" + promptLoader.expertId();
            AgentCard card = AgentCard.builder()
                    .id(agentId)
                    .name(agentId)
                    .description("WorkMate member runtime for expert " + promptLoader.expertId())
                    .build();
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .maxIterations(llm.maxIterations())
                    .build()
                    .configureModelClient(
                            llm.modelProvider(),
                            llm.apiKey(),
                            llm.apiBase(),
                            llm.modelName(),
                            llm.sslVerify());
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.2);
            modelConfig.setMaxTokens(4096);
            agent.configure(config);
            agent.addPromptBuilderSection("workmate_member_expert", promptLoader.systemPrompt(), 10);
            return agent;
        }
    }
}
