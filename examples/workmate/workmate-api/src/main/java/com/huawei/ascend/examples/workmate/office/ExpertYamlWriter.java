package com.huawei.ascend.examples.workmate.office;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Serializes {@link ExpertDefinition} to expert.yaml (symmetric with {@link ExpertRegistry} parser). */
public final class ExpertYamlWriter {

    private ExpertYamlWriter() {}

    public static String defaultPromptFile(ExpertDefinition expert) {
        return expert.isTeam() ? "lead-prompt.md" : "prompt.md";
    }

    public static String render(ExpertDefinition expert, String promptFile) {
        Map<String, Object> yaml = toMap(expert, promptFile);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(yaml);
    }

    public static Map<String, Object> toMap(ExpertDefinition expert, String promptFile) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("id", expert.id());
        yaml.put("name", expert.name());
        putIfPresent(yaml, "description", expert.description());
        yaml.put("expertType", expert.expertType());
        yaml.put("promptFile", promptFile != null && !promptFile.isBlank() ? promptFile : defaultPromptFile(expert));
        putIfPresent(yaml, "defaultInitPrompt", expert.defaultInitPrompt());
        putIfPresent(yaml, "category", expert.category());
        if (!expert.tags().isEmpty()) {
            yaml.put("tags", expert.tags());
        }
        if (!expert.skillCompatibility().isEmpty()) {
            yaml.put("skillCompatibility", expert.skillCompatibility());
        }
        if (!expert.preloadSkills().isEmpty()) {
            yaml.put("preloadSkills", expert.preloadSkills());
        }
        if (expert.maxTurns() != null && expert.maxTurns() > 0) {
            yaml.put("maxTurns", expert.maxTurns());
        }
        if (!expert.quickPrompts().isEmpty()) {
            yaml.put("quickPrompts", expert.quickPrompts());
        }
        putMapIfPresent(yaml, "displayName", expert.displayName());
        putMapIfPresent(yaml, "profession", expert.profession());
        putIfPresent(yaml, "officeCapability", expert.officeCapability());
        putMapIfPresent(yaml, "uiLabels", expert.uiLabels());
        if (expert.isTeam()) {
            putIfPresent(yaml, "collaboration", expert.collaboration());
            putIfPresent(yaml, "runtime", expert.resolvedTeamRuntime());
            putTeamAgentIfPresent(yaml, expert.teamAgent());
            putCoordinationIfPresent(yaml, expert.coordination());
            putLeadIfPresent(yaml, expert.lead());
            putMembersIfPresent(yaml, expert.members());
        }
        return yaml;
    }

    private static void putTeamAgentIfPresent(Map<String, Object> yaml, TeamAgentOverrides teamAgent) {
        if (teamAgent == null) {
            return;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        putIfPresent(raw, "teamMode", teamAgent.teamMode());
        putIfPresent(raw, "spawnMode", teamAgent.spawnMode());
        putIfPresent(raw, "teammateMode", teamAgent.teammateMode());
        if (!raw.isEmpty()) {
            yaml.put("teamAgent", raw);
        }
    }

    private static void putCoordinationIfPresent(Map<String, Object> yaml, CoordinationSpec coordination) {
        if (coordination == null) {
            return;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pattern", coordination.pattern());
        if (coordination.termination() != null && !coordination.termination().isEmpty()) {
            Map<String, Object> term = new LinkedHashMap<>();
            CoordinationSpec.Termination t = coordination.termination();
            if (t.maxIterations() != null) {
                term.put("maxIterations", t.maxIterations());
            }
            if (t.timeBudgetMs() != null) {
                term.put("timeBudgetMs", t.timeBudgetMs());
            }
            putIfPresent(term, "convergence", t.convergence());
            putIfPresent(term, "decider", t.decider());
            if (!term.isEmpty()) {
                raw.put("termination", term);
            }
        }
        putIfPresent(raw, "acceptanceCriteria", coordination.acceptanceCriteria());
        yaml.put("coordination", raw);
    }

    private static void putLeadIfPresent(Map<String, Object> yaml, TeamLeadDefinition lead) {
        if (lead == null) {
            return;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        putIfPresent(raw, "name", lead.name());
        putMapIfPresent(raw, "title", lead.title());
        putIfPresent(raw, "avatar", lead.avatar());
        if (!raw.isEmpty()) {
            yaml.put("lead", raw);
        }
    }

    private static void putMembersIfPresent(Map<String, Object> yaml, List<TeamMemberDefinition> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rawMembers = new ArrayList<>();
        for (TeamMemberDefinition member : members) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", member.id());
            putIfPresent(raw, "name", member.name());
            raw.put("expertId", member.expertId());
            putIfPresent(raw, "role", member.role());
            raw.put("order", member.order());
            putIfPresent(raw, "avatar", member.avatar());
            putIfPresent(raw, "participantRole", member.participantRole());
            putMapIfPresent(raw, "profession", member.profession());
            putIfPresent(raw, "nickname", member.nickname());
            if (member.backend() != null && member.backend() != TeamMemberBackend.LOCAL) {
                raw.put("backend", member.backend().yamlValue());
            }
            putMemberRuntimeIfPresent(raw, member.runtime());
            rawMembers.add(raw);
        }
        yaml.put("members", rawMembers);
    }

    private static void putMemberRuntimeIfPresent(Map<String, Object> memberYaml, TeamMemberRuntimeConfig runtime) {
        if (runtime == null) {
            return;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        putIfPresent(raw, "baseUrl", runtime.baseUrl());
        putIfPresent(raw, "protocol", runtime.protocol());
        putIfPresent(raw, "cliAgent", runtime.cliAgent());
        putMapIfPresent(raw, "adapterConfig", runtime.adapterConfig());
        if (!raw.isEmpty()) {
            memberYaml.put("runtime", raw);
        }
    }

    private static void putIfPresent(Map<String, Object> yaml, String key, String value) {
        if (value != null && !value.isBlank()) {
            yaml.put(key, value);
        }
    }

    private static void putMapIfPresent(Map<String, Object> yaml, String key, Map<String, String> value) {
        if (value != null && !value.isEmpty()) {
            yaml.put(key, value);
        }
    }
}
