package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExpertRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ExpertRegistry.class);

    private final Path officeRoot;
    private final OfficeImportPaths importPaths;
    private volatile Map<String, ExpertRegistryEntry> experts = Map.of();
    private volatile List<String> reloadWarnings = List.of();

    public ExpertRegistry(WorkmateOfficeProperties officeProperties, OfficeImportPaths importPaths) {
        this.officeRoot = officeProperties.resolvedRoot();
        this.importPaths = importPaths;
        reloadAll();
    }

    public int reloadAll() {
        LayeredOfficeAssetLoader.LoadResult<ExpertRegistryEntry> loaded =
                LayeredOfficeAssetLoader.loadSubdirs(expertLayers(true), this::loadExpertDir);
        experts = loaded.entries();
        reloadWarnings = loaded.warnings();
        LOG.info("Loaded {} office expert(s) from {} (+ imports/drafts)", experts.size(), officeRoot);
        return experts.size();
    }

    /** @deprecated Prefer {@link #reloadAll()}; kept for import/upload call sites. */
    public void reloadImports(Path importsDir) {
        reloadAll();
    }

    void reload(Path officeRoot) {
        this.reloadAll();
    }

    public List<String> reloadWarnings() {
        return reloadWarnings;
    }

    public Optional<ExpertRegistryEntry> findEntry(String expertId) {
        if (expertId == null || expertId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(experts.get(expertId));
    }

    public List<ExpertDefinition> listExperts() {
        return experts.values().stream().map(ExpertRegistryEntry::definition).toList();
    }

    public List<ExpertRegistryEntry> listEntries() {
        return List.copyOf(experts.values());
    }

    public Optional<ExpertDefinition> findExpert(String expertId) {
        return findEntry(expertId).map(ExpertRegistryEntry::definition);
    }

    public ExpertDefinition requireExpert(String expertId) {
        return findExpert(expertId).orElseThrow(() -> new ExpertNotFoundException(expertId));
    }

    /** Highest-priority entry excluding {@link OfficeAssetSource#DRAFT} overlays. */
    public Optional<ExpertRegistryEntry> findBaselineEntry(String expertId) {
        if (expertId == null || expertId.isBlank()) {
            return Optional.empty();
        }
        LayeredOfficeAssetLoader.LoadResult<ExpertRegistryEntry> loaded =
                LayeredOfficeAssetLoader.loadSubdirs(expertLayers(false), this::loadExpertDir);
        return Optional.ofNullable(loaded.entries().get(expertId));
    }

    private List<LayeredOfficeAssetLoader.Layer> expertLayers(boolean includeDrafts) {
        List<LayeredOfficeAssetLoader.Layer> layers = new ArrayList<>();
        layers.add(new LayeredOfficeAssetLoader.Layer(officeRoot.resolve("experts"), OfficeAssetSource.BUILTIN, false));
        layers.add(new LayeredOfficeAssetLoader.Layer(
                officeRoot.resolve("experts-market"), OfficeAssetSource.MARKET, true));
        if (importPaths != null) {
            layers.add(new LayeredOfficeAssetLoader.Layer(importPaths.expertsDir(), OfficeAssetSource.IMPORT, false));
            if (includeDrafts) {
                layers.add(new LayeredOfficeAssetLoader.Layer(
                        importPaths.expertsDraftsDir(), OfficeAssetSource.DRAFT, false));
            }
        }
        return layers;
    }

    private void loadExpertDir(
            Path expertDir,
            OfficeAssetSource source,
            Map<String, ExpertRegistryEntry> target,
            List<String> warnings,
            boolean skipIfExists) {
        Path yamlFile = expertDir.resolve("expert.yaml");
        Optional<Map<?, ?>> rawOpt = LayeredOfficeAssetLoader.readYamlMap(yamlFile);
        if (rawOpt.isEmpty()) {
            return;
        }
        Map<?, ?> raw = rawOpt.get();
        try {
            String id = OfficeYaml.stringField(raw, "id");
            if (id == null || id.isBlank()) {
                LOG.warn("Skip expert.yaml without id: {}", yamlFile);
                return;
            }
            if (LayeredOfficeAssetLoader.shouldSkipExisting(id, source, target, skipIfExists)) {
                return;
            }
            String promptFile = OfficeYaml.stringField(raw, "promptFile");
            if (promptFile == null || promptFile.isBlank()) {
                LOG.warn("Skip expert {} — missing promptFile", id);
                return;
            }
            Path promptPath = expertDir.resolve(promptFile).normalize();
            if (!promptPath.startsWith(expertDir) || !java.nio.file.Files.isRegularFile(promptPath)) {
                LOG.warn("Skip expert {} — prompt not found: {}", id, promptPath);
                return;
            }
            String systemPrompt = java.nio.file.Files.readString(promptPath);
            String expertType = OfficeYaml.stringField(raw, "expertType");
            List<TeamMemberDefinition> members = parseMembers(raw, id);
            if ("team".equalsIgnoreCase(expertType) && members.size() < 2) {
                LOG.warn("Skip team expert {} — requires at least 2 members", id);
                return;
            }
            ExpertDefinition expert = new ExpertDefinition(
                    id,
                    OfficeMarketText.normalizeDisplayText(OfficeYaml.stringField(raw, "name")),
                    OfficeMarketText.normalizeDisplayText(OfficeYaml.stringField(raw, "description")),
                    expertType,
                    systemPrompt,
                    OfficeMarketText.normalizeDisplayText(OfficeYaml.stringField(raw, "defaultInitPrompt")),
                    OfficeYaml.stringField(raw, "category"),
                    OfficeMarketText.normalizeTags(OfficeYaml.stringList(raw, "tags")),
                    OfficeYaml.stringList(raw, "skillCompatibility"),
                    sanitizeMembers(members),
                    OfficeYaml.stringField(raw, "collaboration"),
                    sanitizeLead(parseLead(raw)),
                    parseCoordination(raw),
                    OfficeYaml.stringField(raw, "officeCapability"),
                    OfficeMarketText.normalizeDisplayMap(parseUiLabels(raw)),
                    OfficeMarketText.normalizeDisplayMap(parseI18nMap(raw, "displayName")),
                    OfficeMarketText.normalizeDisplayMap(parseI18nMap(raw, "profession")),
                    resolveMaxTurns(raw, systemPrompt),
                    OfficeYaml.stringList(raw, "preloadSkills"),
                    OfficeMarketText.normalizeDisplayList(OfficeYaml.stringList(raw, "quickPrompts")),
                    OfficeYaml.stringField(raw, "runtime"),
                    parseTeamAgent(raw));
            if ("team".equalsIgnoreCase(expertType)) {
                warnDuplicateTeamPersona(expert);
            }
            target.put(id, new ExpertRegistryEntry(expert, source, expertDir, promptFile));
        } catch (java.io.IOException ex) {
            LOG.warn("Failed to load expert from {}: {}", yamlFile, ex.getMessage());
            warnings.add("Failed to load expert from " + yamlFile + ": " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<TeamMemberDefinition> parseMembers(Map<?, ?> raw, String teamId) {
        Object value = raw.get("members");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<TeamMemberDefinition> members = new ArrayList<>();
        int index = 0;
        for (Object item : list) {
            index++;
            if (!(item instanceof Map<?, ?> memberRaw)) {
                continue;
            }
            String memberId = OfficeYaml.stringField(memberRaw, "id");
            String expertId = OfficeYaml.stringField(memberRaw, "expertId");
            if (memberId == null || memberId.isBlank() || expertId == null || expertId.isBlank()) {
                LOG.warn("Skip invalid member in team {} — missing id or expertId", teamId);
                continue;
            }
            int order = OfficeYaml.intField(memberRaw, "order", index);
            try {
                members.add(new TeamMemberDefinition(
                        memberId,
                        OfficeYaml.stringField(memberRaw, "name"),
                        expertId,
                        OfficeYaml.stringField(memberRaw, "role"),
                        order,
                        OfficeYaml.stringField(memberRaw, "avatar"),
                        OfficeYaml.stringField(memberRaw, "participantRole"),
                        parseI18nMap(memberRaw, "profession"),
                        OfficeYaml.stringField(memberRaw, "nickname"),
                        parseMemberBackend(memberRaw),
                        parseMemberRuntime(memberRaw)));
            } catch (IllegalArgumentException ex) {
                LOG.warn("Skip invalid member in team {}: {}", teamId, ex.getMessage());
            }
        }
        members.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return members;
    }

    private static TeamLeadDefinition sanitizeLead(TeamLeadDefinition lead) {
        if (lead == null) {
            return null;
        }
        return new TeamLeadDefinition(
                OfficeMarketText.normalizeDisplayText(lead.name()),
                OfficeMarketText.normalizeDisplayMap(lead.title()),
                lead.avatar());
    }

    private static List<TeamMemberDefinition> sanitizeMembers(List<TeamMemberDefinition> members) {
        if (members == null || members.isEmpty()) {
            return members == null ? List.of() : members;
        }
        return members.stream()
                .map(member -> new TeamMemberDefinition(
                        member.id(),
                        OfficeMarketText.normalizeDisplayText(member.name()),
                        member.expertId(),
                        OfficeMarketText.normalizeDisplayText(member.role()),
                        member.order(),
                        member.avatar(),
                        member.participantRole(),
                        OfficeMarketText.normalizeDisplayMap(member.profession()),
                        OfficeMarketText.normalizeDisplayText(member.nickname()),
                        member.backend(),
                        member.runtime()))
                .toList();
    }

    private static TeamMemberBackend parseMemberBackend(Map<?, ?> memberRaw) {
        String value = OfficeYaml.stringField(memberRaw, "backend");
        if (value == null || value.isBlank()) {
            return TeamMemberBackend.LOCAL;
        }
        try {
            return TeamMemberBackend.fromYaml(value);
        } catch (IllegalArgumentException ex) {
            LOG.warn("Invalid member backend '{}', defaulting to local", value);
            return TeamMemberBackend.LOCAL;
        }
    }

    private static TeamMemberRuntimeConfig parseMemberRuntime(Map<?, ?> memberRaw) {
        Object value = memberRaw.get("runtime");
        if (!(value instanceof Map<?, ?> runtimeRaw)) {
            return null;
        }
        return new TeamMemberRuntimeConfig(
                OfficeYaml.stringField(runtimeRaw, "baseUrl"),
                OfficeYaml.stringField(runtimeRaw, "protocol"),
                OfficeYaml.stringField(runtimeRaw, "cliAgent"),
                parseStringMap(runtimeRaw, "adapterConfig"));
    }

    private static TeamAgentOverrides parseTeamAgent(Map<?, ?> raw) {
        Object value = raw.get("teamAgent");
        if (!(value instanceof Map<?, ?> teamAgentRaw)) {
            return null;
        }
        return new TeamAgentOverrides(
                OfficeYaml.stringField(teamAgentRaw, "teamMode"),
                OfficeYaml.stringField(teamAgentRaw, "spawnMode"),
                OfficeYaml.stringField(teamAgentRaw, "teammateMode"));
    }

    private static Map<String, String> parseStringMap(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return result;
    }

    private static TeamLeadDefinition parseLead(Map<?, ?> raw) {
        Object value = raw.get("lead");
        if (!(value instanceof Map<?, ?> leadRaw)) {
            return null;
        }
        return new TeamLeadDefinition(
                OfficeYaml.stringField(leadRaw, "name"),
                parseI18nMap(leadRaw, "title"),
                OfficeYaml.stringField(leadRaw, "avatar"));
    }

    private static CoordinationSpec parseCoordination(Map<?, ?> raw) {
        Object value = raw.get("coordination");
        if (!(value instanceof Map<?, ?> coordRaw)) {
            return null;
        }
        String pattern = OfficeYaml.stringField(coordRaw, "pattern");
        CoordinationSpec.Termination termination = null;
        if (coordRaw.get("termination") instanceof Map<?, ?> termRaw) {
            CoordinationSpec.Termination parsed = new CoordinationSpec.Termination(
                    OfficeYaml.boxedInt(termRaw, "maxIterations"),
                    OfficeYaml.boxedLong(termRaw, "timeBudgetMs"),
                    OfficeYaml.stringField(termRaw, "convergence"),
                    OfficeYaml.stringField(termRaw, "decider"));
            if (!parsed.isEmpty()) {
                termination = parsed;
            }
        }
        return new CoordinationSpec(pattern, termination, OfficeYaml.stringField(coordRaw, "acceptanceCriteria"));
    }

    /** Prefer expert.yaml {@code maxTurns}; fall back to prompt frontmatter (the reference workbench agent .md style). */
    private static Integer resolveMaxTurns(Map<?, ?> raw, String systemPrompt) {
        Integer fromYaml = OfficeYaml.boxedInt(raw, "maxTurns");
        if (fromYaml != null && fromYaml > 0) {
            return fromYaml;
        }
        return parsePromptFrontmatterInt(systemPrompt, "maxTurns");
    }

    static Integer parsePromptFrontmatterInt(String prompt, String key) {
        if (prompt == null || !prompt.startsWith("---")) {
            return null;
        }
        int end = prompt.indexOf("\n---", 3);
        if (end < 0) {
            return null;
        }
        String frontmatter = prompt.substring(3, end);
        String prefix = key + ":";
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                if (value.isEmpty()) {
                    return null;
                }
                try {
                    return Integer.valueOf(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Map<String, String> parseUiLabels(Map<?, ?> raw) {
        Object value = raw.get("uiLabels");
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> parseI18nMap(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            return Map.copyOf(result);
        }
        if (value instanceof String text && !text.isBlank()) {
            return Map.of("zh", text.trim());
        }
        return Map.of();
    }

    private static void warnDuplicateTeamPersona(ExpertDefinition expert) {
        for (TeamMemberDefinition member : expert.members()) {
            String zhProfession = member.resolvedProfession("zh");
            if (!zhProfession.isBlank() && zhProfession.equals(member.name())) {
                LOG.warn(
                        "Team {} member {} — profession duplicates name (team-spec)",
                        expert.id(),
                        member.id());
            }
            if (member.nickname() != null
                    && !member.nickname().isBlank()
                    && (member.nickname().equals(member.name()) || member.nickname().equals(zhProfession))) {
                LOG.warn(
                        "Team {} member {} — nickname duplicates name or profession (team-spec)",
                        expert.id(),
                        member.id());
            }
        }
    }
}
