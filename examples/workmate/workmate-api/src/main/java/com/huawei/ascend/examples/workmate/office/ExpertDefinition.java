package com.huawei.ascend.examples.workmate.office;

import java.util.List;
import java.util.Map;

public record ExpertDefinition(
        String id,
        String name,
        String description,
        String expertType,
        String systemPrompt,
        String defaultInitPrompt,
        String category,
        List<String> tags,
        List<String> skillCompatibility,
        List<TeamMemberDefinition> members,
        String collaboration,
        TeamLeadDefinition lead,
        CoordinationSpec coordination,
        String officeCapability,
        Map<String, String> uiLabels,
        Map<String, String> displayName,
        Map<String, String> profession,
        Integer maxTurns,
        List<String> preloadSkills,
        List<String> quickPrompts,
        String teamRuntime,
        TeamAgentOverrides teamAgent) {

    /** Backward-compatible constructor (pre A2: without displayName/profession/maxTurns/preloadSkills). */
    public ExpertDefinition(
            String id,
            String name,
            String description,
            String expertType,
            String systemPrompt,
            String defaultInitPrompt,
            String category,
            List<String> tags,
            List<String> skillCompatibility,
            List<TeamMemberDefinition> members,
            String collaboration,
            TeamLeadDefinition lead,
            CoordinationSpec coordination,
            String officeCapability,
            Map<String, String> uiLabels) {
        this(id, name, description, expertType, systemPrompt, defaultInitPrompt, category, tags,
                skillCompatibility, members, collaboration, lead, coordination, officeCapability,
                uiLabels, null, null, null, null, null, null, null);
    }

    /** Backward-compatible constructor (pre quickPrompts). */
    public ExpertDefinition(
            String id,
            String name,
            String description,
            String expertType,
            String systemPrompt,
            String defaultInitPrompt,
            String category,
            List<String> tags,
            List<String> skillCompatibility,
            List<TeamMemberDefinition> members,
            String collaboration,
            TeamLeadDefinition lead,
            CoordinationSpec coordination,
            String officeCapability,
            Map<String, String> uiLabels,
            Map<String, String> displayName,
            Map<String, String> profession,
            Integer maxTurns,
            List<String> preloadSkills) {
        this(id, name, description, expertType, systemPrompt, defaultInitPrompt, category, tags,
                skillCompatibility, members, collaboration, lead, coordination, officeCapability,
                uiLabels, displayName, profession, maxTurns, preloadSkills, null, null, null);
    }

    /** Backward-compatible constructor (pre W51 team runtime). */
    public ExpertDefinition(
            String id,
            String name,
            String description,
            String expertType,
            String systemPrompt,
            String defaultInitPrompt,
            String category,
            List<String> tags,
            List<String> skillCompatibility,
            List<TeamMemberDefinition> members,
            String collaboration,
            TeamLeadDefinition lead,
            CoordinationSpec coordination,
            String officeCapability,
            Map<String, String> uiLabels,
            Map<String, String> displayName,
            Map<String, String> profession,
            Integer maxTurns,
            List<String> preloadSkills,
            List<String> quickPrompts) {
        this(id, name, description, expertType, systemPrompt, defaultInitPrompt, category, tags,
                skillCompatibility, members, collaboration, lead, coordination, officeCapability,
                uiLabels, displayName, profession, maxTurns, preloadSkills, quickPrompts, null, null);
    }

    public ExpertDefinition {
        if (uiLabels == null) {
            uiLabels = Map.of();
        }
        if (displayName == null) {
            displayName = Map.of();
        }
        if (profession == null) {
            profession = Map.of();
        }
        if (preloadSkills == null) {
            preloadSkills = List.of();
        }
        if (quickPrompts == null) {
            quickPrompts = List.of();
        }
        if (tags == null) {
            tags = List.of();
        }
        if (skillCompatibility == null) {
            skillCompatibility = List.of();
        }
        if (members == null) {
            members = List.of();
        }
        if (expertType == null || expertType.isBlank()) {
            expertType = "agent";
        }
        boolean team = "team".equalsIgnoreCase(expertType);
        // 向后兼容：无 coordination 时，由 collaboration 推导拓扑（ADR-013 §1）。
        if (team && coordination == null) {
            String pattern = "parallel".equalsIgnoreCase(collaboration)
                    ? CoordinationSpec.AGENT_TEAM
                    : CoordinationSpec.ORCHESTRATOR;
            coordination = new CoordinationSpec(pattern, null);
        }
        if (collaboration == null || collaboration.isBlank()) {
            collaboration = team ? "sequential" : null;
        }
        // lead 仅当拓扑含协调者时存在（ADR-013：leader 可选/派生）。
        if (team && lead == null && coordination != null && coordination.hasLead()) {
            lead = TeamLeadDefinition.fallback(name);
        }
    }

    public boolean isTeam() {
        return "team".equalsIgnoreCase(expertType);
    }

    /** Effective max ReAct turns: per-expert override or {@code null} to fall back to global. */
    public Integer effectiveMaxTurns() {
        return maxTurns != null && maxTurns > 0 ? maxTurns : null;
    }

    public String resolvedDisplayName(String lang) {
        if (displayName.containsKey(lang)) {
            return displayName.get(lang);
        }
        if (displayName.containsKey("zh")) {
            return displayName.get("zh");
        }
        if (!displayName.isEmpty()) {
            return displayName.values().iterator().next();
        }
        return name;
    }

    public String resolvedProfession(String lang) {
        if (profession.containsKey(lang)) {
            return profession.get(lang);
        }
        if (profession.containsKey("zh")) {
            return profession.get("zh");
        }
        if (!profession.isEmpty()) {
            return profession.values().iterator().next();
        }
        return "";
    }

    public String coordinationPattern() {
        return coordination != null ? coordination.pattern() : CoordinationSpec.ORCHESTRATOR;
    }

    /** Effective team runtime kind from expert.yaml {@code runtime} field; null → orchestrator default. */
    public String resolvedTeamRuntime() {
        return teamRuntime != null && !teamRuntime.isBlank() ? teamRuntime.trim() : null;
    }

    public boolean isOfficeCapable() {
        return OfficeArtifactContract.isOfficeCapable(this);
    }

    public String resolvedOfficeCapability() {
        return OfficeArtifactContract.resolveCapability(this);
    }
}
