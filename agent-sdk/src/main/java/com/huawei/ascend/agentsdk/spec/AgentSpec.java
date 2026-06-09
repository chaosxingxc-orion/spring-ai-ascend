package com.huawei.ascend.agentsdk.spec;

import com.huawei.ascend.agentsdk.spec.model.ModelSpec;
import com.huawei.ascend.agentsdk.spec.prompt.PromptSpec;
import com.huawei.ascend.agentsdk.spec.skill.SkillSourceSpec;
import com.huawei.ascend.agentsdk.spec.skill.SkillSpec;
import com.huawei.ascend.agentsdk.spec.tool.ToolSpec;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record AgentSpec(
        String schema,
        String name,
        String displayName,
        String description,
        Map<String, Object> metadata,
        Path cacheRoot,
        String frameworkType,
        String agentType,
        Map<String, Object> frameworkOptions,
        ModelSpec modelSpec,
        PromptSpec promptSpec,
        List<SkillSourceSpec> skillSources,
        List<SkillSpec> skillSpecs,
        List<ToolSpec> toolSpecs) {

    public AgentSpec {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        frameworkOptions = frameworkOptions == null ? Map.of() : Map.copyOf(frameworkOptions);
        skillSources = skillSources == null ? List.of() : List.copyOf(skillSources);
        skillSpecs = skillSpecs == null ? List.of() : List.copyOf(skillSpecs);
        toolSpecs = toolSpecs == null ? List.of() : List.copyOf(toolSpecs);
    }
}

