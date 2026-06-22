package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final SkillHubProvider skillHubProvider;

    public OpenJiuwenSkillHubInstaller(SkillHubProvider skillHubProvider) {
        this.skillHubProvider = Objects.requireNonNull(skillHubProvider, "skillHubProvider");
    }

    public void install(BaseAgent agent, AgentExecutionContext context) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(context, "context");
        List<SkillSummary> summaries = safeSummaries(context);
        int installed = 0;
        for (SkillSummary summary : summaries) {
            SkillDefinition definition = loadSkill(context, summary.skillId());
            if (definition == null) {
                continue;
            }
            for (String path : openJiuwenSkillPaths(definition.metadata())) {
                agent.registerSkill(path);
                installed++;
                LOG.info("installed openjiuwen skill tenantId={} sessionId={} taskId={} skillId={} path={}",
                        context.getScope().tenantId(),
                        context.getScope().sessionId(),
                        context.getScope().taskId(),
                        definition.skillId(),
                        path);
            }
        }
        LOG.info("skillhub install finished tenantId={} sessionId={} taskId={} summaries={} installed={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                summaries.size(),
                installed);
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
}
