package com.huawei.ascend.examples.financial.planagent;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single-agent configuration for the plan-agent.
 *
 * <p>Mirrors the reference {@code MainAgentConfiguration} but drops the
 * role/profile conditional (this module always creates its one agent). The
 * LLM is required; the API key/base/model come from {@code LLM_API_KEY} /
 * {@code LLM_API_BASE} / {@code LLM_MODEL} / {@code LLM_SSL_VERIFY} with the
 * provider fixed to {@code openai}. {@code maxIterations=12} gives the ReAct
 * loop room to fan one request out to several versatile-call legs plus a
 * final summary.
 */
@Configuration(proxyBeanMethods = false)
public class PlanAgentConfiguration {
    static final String AGENT_ID = "plan-agent";

    private static final Logger LOG = LoggerFactory.getLogger(PlanAgentConfiguration.class);

    private static final String SYSTEM_PROMPT = """
            You are a banking assistant. The user may ask several things in one sentence
            (check balance, transfer money to one or more people). Decompose the request
            into ordered atomic tasks and execute them one by one.

            Follow the transfer skill to decompose the request and assemble the request
            body JSON for each task, then call the available versatile-call tool.

            After all tasks finish, summarise every result for the user in Chinese.
            """;

    @Bean
    OpenJiuwenAgentRuntimeHandler planAgentHandler(
            @Value("${plan-agent.model-provider:openai}") String modelProvider,
            @Value("${plan-agent.api-key:}") String apiKey,
            @Value("${plan-agent.api-base:http://localhost:4000/v1}") String apiBase,
            @Value("${plan-agent.model-name:gpt-4o-mini}") String modelName,
            @Value("${plan-agent.ssl-verify:true}") boolean sslVerify) {
        return new PlanAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard planAgentCard() {
        return AgentCards.create(AGENT_ID,
                "One-sentence bank-transfer planning agent. Decomposes a request into ordered "
                        + "atomic tasks (balance query, transfers) and calls versatile-call per task.");
    }

    private static final class PlanAgentHandler extends OpenJiuwenAgentRuntimeHandler {
        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        private PlanAgentHandler(String modelProvider, String apiKey, String apiBase, String modelName,
                boolean sslVerify) {
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
                    .description("Plan agent that decomposes a banking request and calls versatile-call per task.")
                    .build();
            ReActAgent agent = new ReActAgent(card);

            // Register a local SysOperation so the Runner exposes system tools
            // (readFile) that the skill prompt instructs the LLM to use to read
            // SKILL.md. executeCmd / executeCode are intentionally NOT registered —
            // this agent delegates work to versatile-call and has no reason to run
            // shell or arbitrary code on the host.
            SysOperationCard sysOpCard = SysOperationCard.builder()
                    .id(AGENT_ID)
                    .mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder().workDir(null).build())
                    .build();
            Runner.resourceMgr().addSysOperation(sysOpCard, null);

            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(12)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.0);
            modelConfig.setMaxTokens(512);
            // SysOperationId must match the registered SysOperationCard so
            // lazyInitSkill() initialises SkillUtil with the correct id and
            // getSysOpToolCards() finds the tools for this SysOp.
            config.setSysOperationId(sysOpCard.getId());
            agent.configure(config);

            // Inject readFile so the LLM can follow the "read SKILL.md"
            // instruction generated by SkillUtil.getSkillPrompt().
            addSysOpTool(agent, sysOpCard.getId(), "fs", "readFile");

            // Register the skill from the local skills/ directory. The working
            // directory is the example module root, so "skills" resolves directly.
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

        private static void addSysOpTool(ReActAgent agent, String sysOpId, String operationName,
                String toolName) {
            Object toolCard = Runner.resourceMgr()
                    .getSysOpToolCards(sysOpId, operationName, toolName);
            if (toolCard != null) {
                agent.getAbilityManager().add(toolCard);
            }
        }
    }
}
