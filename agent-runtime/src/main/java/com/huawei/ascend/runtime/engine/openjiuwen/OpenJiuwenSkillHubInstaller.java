package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenJiuwen-local adapter that installs SkillHub definitions into an
 * OpenJiuwen {@link BaseAgent}.
 *
 * <p>The runtime-neutral SkillHub SPI exposes summaries and full definitions.
 * OpenJiuwen consumes local skill paths declared in definition metadata and
 * delegates the actual skill parsing/loading to {@link BaseAgent#registerSkill(Object)}.
 */
public final class OpenJiuwenSkillHubInstaller {
    public static final String METADATA_OPENJIUWEN_SKILL_PATH = "openjiuwen.skill.path";
    public static final String METADATA_OPENJIUWEN_SKILL_PATHS = "openjiuwen.skill.paths";

    private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenSkillHubInstaller.class);
    private static final String RUNTIME_SKILLHUB_SECTION = "runtime_skillhub";
    private static final int RUNTIME_SKILLHUB_SECTION_PRIORITY = 91;

    private final SkillHubProvider skillHubProvider;

    public OpenJiuwenSkillHubInstaller(SkillHubProvider skillHubProvider) {
        this.skillHubProvider = Objects.requireNonNull(skillHubProvider, "skillHubProvider");
    }

    public void install(BaseAgent agent, AgentExecutionContext context) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(context, "context");
        install(context, agent, agent::registerSkill, "openjiuwen agent=" + agent.getCard().getId());
    }

    public void install(DeepAgent agent, AgentExecutionContext context) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(context, "context");
        BaseAgent innerAgent = agent.getAgent();
        if (innerAgent.getSkillUtil() == null) {
            LOG.warn("skillhub installer skipped for openjiuwen deepagent={} because inner ReActAgent skill runtime "
                    + "is not configured", agent.getCard().getId());
            return;
        }
        install(context, innerAgent, innerAgent::registerSkill, "openjiuwen deepagent=" + agent.getCard().getId());
    }

    private void install(AgentExecutionContext context, BaseAgent agent, Consumer<Object> registrar, String target) {
        List<SkillSummary> summaries = safeSummaries(context);
        int installed = 0;
        List<SkillDefinition> loadedDefinitions = new ArrayList<>();
        for (SkillSummary summary : summaries) {
            SkillDefinition definition = loadSkill(context, summary.skillId());
            if (definition == null) {
                continue;
            }
            loadedDefinitions.add(definition);
            for (String path : openJiuwenSkillPaths(definition.metadata())) {
                int before = skillCount(agent);
                registrar.accept(path);
                int after = skillCount(agent);
                if (after > before) {
                    installed++;
                    LOG.info("installed openjiuwen skill tenantId={} sessionId={} taskId={} target={} skillId={} "
                                    + "path={}",
                            context.getScope().tenantId(),
                            context.getScope().sessionId(),
                            context.getScope().taskId(),
                            target,
                            definition.skillId(),
                            path);
                } else {
                    LOG.warn("openjiuwen skill registration was not observed tenantId={} sessionId={} taskId={} "
                                    + "target={} skillId={} path={} beforeCount={} afterCount={} hint={}",
                            context.getScope().tenantId(),
                            context.getScope().sessionId(),
                            context.getScope().taskId(),
                            target,
                            definition.skillId(),
                            path,
                            before,
                            after,
                            "SKILL.md may need YAML frontmatter with description, or the agent may not have "
                                    + "initialized SkillUtil");
                }
            }
        }
        int injected = injectRuntimeSkillSection(agent, loadedDefinitions);
        LOG.info("skillhub install finished tenantId={} sessionId={} taskId={} target={} summaries={} installed={} "
                        + "injected={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                target,
                summaries.size(),
                installed,
                injected);
    }

    private List<SkillSummary> safeSummaries(AgentExecutionContext context) {
        List<SkillSummary> summaries = skillHubProvider.listSkills(context);
        return summaries == null ? List.of() : summaries;
    }

    private SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        return skillHubProvider.loadSkill(context, skillId);
    }

    private static List<String> openJiuwenSkillPaths(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        Object singlePath = metadata.get(METADATA_OPENJIUWEN_SKILL_PATH);
        addPath(paths, singlePath);
        Object manyPaths = metadata.get(METADATA_OPENJIUWEN_SKILL_PATHS);
        if (manyPaths instanceof Iterable<?> iterable) {
            for (Object path : iterable) {
                addPath(paths, path);
            }
        } else {
            addPath(paths, manyPaths);
        }
        return List.copyOf(paths);
    }

    private static void addPath(List<String> paths, Object candidate) {
        if (candidate instanceof String path && !path.isBlank()) {
            paths.add(path);
        }
    }

    private static int skillCount(BaseAgent agent) {
        if (agent.getSkillUtil() == null || agent.getSkillUtil().getSkillManager() == null) {
            return -1;
        }
        return agent.getSkillUtil().getSkillManager().count();
    }

    private static int injectRuntimeSkillSection(BaseAgent agent, List<SkillDefinition> definitions) {
        if (!(agent instanceof ReActAgent reactAgent) || definitions.isEmpty()) {
            return 0;
        }
        String content = runtimeSkillHubPrompt(definitions);
        if (content.isBlank()) {
            return 0;
        }
        reactAgent.addPromptBuilderSection(RUNTIME_SKILLHUB_SECTION, content, RUNTIME_SKILLHUB_SECTION_PRIORITY);
        return definitions.size();
    }

    private static String runtimeSkillHubPrompt(List<SkillDefinition> definitions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Runtime SkillHub has loaded the following skills. ")
                .append("Use these instructions directly when they are relevant to the user request.\n");
        Set<String> seen = new LinkedHashSet<>();
        for (SkillDefinition definition : definitions) {
            if (!seen.add(definition.skillId())) {
                continue;
            }
            prompt.append("\nSkill ID: ").append(definition.skillId()).append('\n')
                    .append("Name: ").append(definition.name()).append('\n');
            if (!definition.description().isBlank()) {
                prompt.append("Description: ").append(definition.description()).append('\n');
            }
            if (!definition.instructions().isBlank()) {
                prompt.append("Instructions:\n").append(definition.instructions().trim()).append('\n');
            }
        }
        return prompt.toString();
    }
}
