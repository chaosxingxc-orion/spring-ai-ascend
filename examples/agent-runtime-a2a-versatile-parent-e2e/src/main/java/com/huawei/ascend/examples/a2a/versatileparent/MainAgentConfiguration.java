package com.huawei.ascend.examples.a2a.versatileparent;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the main (parent) agent — an OpenJiuwen ReAct LLM agent
 * that discovers and calls the remote versatile child agent via the A2A
 * remote-agent tool mechanism.
 *
 * <p>Activate with {@code --spring.profiles.active=main}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sample.versatile-parent.role", havingValue = "main")
public class MainAgentConfiguration {
    static final String AGENT_ID = "main-parent";

    private static final Logger LOG = LoggerFactory.getLogger(MainAgentConfiguration.class);

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant. When the user asks you to perform a task
            that requires external workflow execution, extract the relevant business
            parameters from the user's message and call the available tool with a
            JSON object containing those parameters.

            Follow the hotel-booking skill for hotel reservation requests.

            After receiving a tool result, summarize it briefly for the user.
            """;

    @Bean
    OpenJiuwenAgentRuntimeHandler mainAgentHandler(
            @Value("${sample.versatile-parent.llm.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.versatile-parent.llm.api-key:${SAA_SAMPLE_LLM_API_KEY:}}")
            String apiKey,
            @Value("${sample.versatile-parent.llm.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:}}")
            String apiBase,
            @Value("${sample.versatile-parent.llm.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}")
            String modelName,
            @Value("${sample.versatile-parent.llm.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:true}}")
            boolean sslVerify) {
        return new MainAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard mainAgentCard() {
        return com.huawei.ascend.runtime.engine.a2a.AgentCards.create(AGENT_ID,
                "Main parent OpenJiuwen LLM agent that calls a remote Versatile child agent.");
    }

    private static final class MainAgentHandler extends OpenJiuwenAgentRuntimeHandler {
        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        private MainAgentHandler(String modelProvider, String apiKey, String apiBase,
                String modelName, boolean sslVerify) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("Main parent LLM agent that calls remote Versatile child via A2A tool.")
                    .build();
            ReActAgent agent = new ReActAgent(card);

            // Register a local SysOperation so the Runner exposes system
            // tools (readFile, executeCode, executeCmd) that the skill
            // prompt instructs the LLM to use.
            SysOperationCard sysOpCard = SysOperationCard.builder()
                    .id(AGENT_ID)
                    .mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder().workDir(null).build())
                    .build();
            Runner.resourceMgr().addSysOperation(sysOpCard, null);

            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(4)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.0);
            modelConfig.setMaxTokens(512);
            // SysOperationId must match the registered SysOperationCard so
            // lazyInitSkill() can initialise SkillUtil with the correct id,
            // and getSysOpToolCards() finds the tools for this SysOp.
            config.setSysOperationId(sysOpCard.getId());
            agent.configure(config);

            // Inject readFile so the LLM can follow the "read SKILL.md"
            // instruction generated by SkillUtil.getSkillPrompt().
            // executeCmd / executeCode are intentionally NOT registered —
            // the parent agent delegates work to the remote versatile child
            // and has no legitimate reason to execute shell commands or
            // arbitrary code on the host.
            addSysOpTool(agent, sysOpCard.getId(), "fs", "readFile");

            // Register the skill from the local skills/ directory.
            // The working directory is the example module root, so "skills"
            // resolves directly — Path.of("examples", ...) would double-nest.
            Path skillsDir = Path.of("skills");
            LOG.info("registering skill path={} absolute={} exists={}",
                    skillsDir, skillsDir.toAbsolutePath().normalize(),
                    java.nio.file.Files.exists(skillsDir));
            agent.registerSkill(skillsDir.toString());
            boolean hasSkill = agent.getSkillUtil() != null && agent.getSkillUtil().hasSkill();
            LOG.info("skill registered hasSkillUtil={} hasSkill={} skillCount={}",
                    agent.getSkillUtil() != null, hasSkill,
                    agent.getSkillUtil() != null ? agent.getSkillUtil().getSkillManager().count() : 0);

            return agent;
        }

        private static void addSysOpTool(ReActAgent agent, String sysOpId,
                String operationName, String toolName) {
            Object toolCard = Runner.resourceMgr()
                    .getSysOpToolCards(sysOpId, operationName, toolName);
            if (toolCard != null) {
                agent.getAbilityManager().add(toolCard);
            }
        }
    }
}
