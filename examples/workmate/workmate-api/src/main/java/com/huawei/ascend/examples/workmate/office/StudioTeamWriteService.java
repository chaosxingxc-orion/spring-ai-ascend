package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioCoordinationWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioLeadWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioRuntimePreviewResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamAgentWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamMemberView;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamMemberWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamViewResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamWriteRequest;
import com.huawei.ascend.examples.workmate.office.ExpertYamlWriter;
import com.huawei.ascend.examples.workmate.team.agent.TeamRuntimeKind;
import com.huawei.ascend.examples.workmate.team.agent.TeamRuntimeRouter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StudioTeamWriteService {

    private final ExpertRegistry expertRegistry;
    private final StudioDraftStore draftStore;
    private final TeamRuntimeRouter teamRuntimeRouter;

    public StudioTeamWriteService(
            ExpertRegistry expertRegistry, StudioDraftStore draftStore, TeamRuntimeRouter teamRuntimeRouter) {
        this.expertRegistry = expertRegistry;
        this.draftStore = draftStore;
        this.teamRuntimeRouter = teamRuntimeRouter;
    }

    public StudioTeamViewResponse getTeam(String teamId) {
        String safeId = OfficeImportValidator.requireSafeId(teamId, "Team");
        ExpertDefinition team = expertRegistry.requireExpert(safeId);
        if (!team.isTeam()) {
            throw new IllegalArgumentException("Expert is not a team: " + safeId);
        }
        ExpertRegistryEntry entry =
                expertRegistry.findEntry(safeId).orElseThrow(() -> new ExpertNotFoundException(safeId));
        return buildTeamView(entry, team);
    }

    public StudioTeamViewResponse createTeam(StudioTeamWriteRequest request) {
        NormalizedTeamWrite normalized = normalizeTeamRequest(request, null, true);
        if (expertRegistry.findExpert(normalized.teamId()).isPresent()) {
            throw new IllegalArgumentException("Team already exists: " + normalized.teamId());
        }
        writeTeamDraft(normalized);
        expertRegistry.reloadAll();
        return getTeam(normalized.teamId());
    }

    public StudioTeamViewResponse updateTeam(String teamId, StudioTeamWriteRequest request) {
        NormalizedTeamWrite normalized = normalizeTeamRequest(request, teamId, false);
        if (expertRegistry.findExpert(normalized.teamId()).isEmpty()) {
            throw new ExpertNotFoundException(normalized.teamId());
        }
        writeTeamDraft(normalized);
        expertRegistry.reloadAll();
        return getTeam(normalized.teamId());
    }

    public ImportValidationResponse validateTeam(StudioTeamWriteRequest request, String pathTeamId) {
        try {
            normalizeTeamRequest(request, pathTeamId, pathTeamId == null);
            return new ImportValidationResponse(true, "OK");
        } catch (IllegalArgumentException ex) {
            return new ImportValidationResponse(false, ex.getMessage());
        }
    }

    public StudioRuntimePreviewResponse previewRuntime(StudioTeamWriteRequest request, String pathTeamId) {
        NormalizedTeamWrite normalized = normalizeTeamRequest(request, pathTeamId, pathTeamId == null);
        return buildRuntimePreview(normalized.definition());
    }

    public StudioRuntimePreviewResponse previewRuntimeForTeam(String teamId) {
        ExpertDefinition team = expertRegistry.requireExpert(OfficeImportValidator.requireSafeId(teamId, "Team"));
        if (!team.isTeam()) {
            throw new IllegalArgumentException("Expert is not a team: " + teamId);
        }
        return buildRuntimePreview(team);
    }

    public StudioTeamViewResponse updateCoordination(String teamId, StudioCoordinationWriteRequest coordination) {
        StudioTeamWriteRequest base = toWriteRequest(requireTeamEntry(teamId));
        return updateTeam(teamId, copyTeam(base, coordination, null, null, null, null));
    }

    public StudioTeamViewResponse updateRuntime(String teamId, String teamRuntime) {
        StudioTeamWriteRequest base = toWriteRequest(requireTeamEntry(teamId));
        return updateTeam(teamId, copyTeam(base, base.coordination(), teamRuntime, null, null, base.members()));
    }

    public StudioTeamViewResponse updateLead(String teamId, StudioLeadWriteRequest lead, String promptContent) {
        StudioTeamWriteRequest base = toWriteRequest(requireTeamEntry(teamId));
        String leadPrompt = promptContent != null && !promptContent.isBlank() ? promptContent : base.promptContent();
        return updateTeam(teamId, copyTeam(base, base.coordination(), base.teamRuntime(), lead, leadPrompt, base.members()));
    }

    public StudioTeamViewResponse addMember(String teamId, StudioTeamMemberWriteRequest member) {
        StudioTeamWriteRequest base = toWriteRequest(requireTeamEntry(teamId));
        List<StudioTeamMemberWriteRequest> members = new ArrayList<>(base.members() == null ? List.of() : base.members());
        members.add(member);
        return updateTeam(teamId, copyTeam(base, base.coordination(), base.teamRuntime(), base.lead(), base.promptContent(), members));
    }

    public StudioTeamViewResponse updateMember(String teamId, String memberId, StudioTeamMemberWriteRequest member) {
        StudioTeamWriteRequest base = toWriteRequest(requireTeamEntry(teamId));
        List<StudioTeamMemberWriteRequest> members = new ArrayList<>();
        boolean found = false;
        for (StudioTeamMemberWriteRequest existing : base.members() == null ? List.<StudioTeamMemberWriteRequest>of() : base.members()) {
            if (memberId.equals(existing.id())) {
                members.add(member);
                found = true;
            } else {
                members.add(existing);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Team member not found: " + memberId);
        }
        return updateTeam(teamId, copyTeam(base, base.coordination(), base.teamRuntime(), base.lead(), base.promptContent(), members));
    }

    public StudioTeamViewResponse deleteMember(String teamId, String memberId) {
        StudioTeamWriteRequest base = toWriteRequest(requireTeamEntry(teamId));
        List<StudioTeamMemberWriteRequest> members = (base.members() == null ? List.<StudioTeamMemberWriteRequest>of() : base.members())
                .stream()
                .filter(member -> !memberId.equals(member.id()))
                .toList();
        if (members.size() == base.members().size()) {
            throw new IllegalArgumentException("Team member not found: " + memberId);
        }
        if (members.size() < 2) {
            throw new IllegalArgumentException("Team requires at least 2 members");
        }
        return updateTeam(teamId, copyTeam(base, base.coordination(), base.teamRuntime(), base.lead(), base.promptContent(), members));
    }

    private ExpertRegistryEntry requireTeamEntry(String teamId) {
        String safeId = OfficeImportValidator.requireSafeId(teamId, "Team");
        ExpertRegistryEntry entry =
                expertRegistry.findEntry(safeId).orElseThrow(() -> new ExpertNotFoundException(safeId));
        if (!entry.definition().isTeam()) {
            throw new IllegalArgumentException("Expert is not a team: " + safeId);
        }
        return entry;
    }

    private StudioTeamWriteRequest toWriteRequest(ExpertRegistryEntry entry) {
        ExpertDefinition team = entry.definition();
        StudioCoordinationWriteRequest coordination = null;
        if (team.coordination() != null) {
            CoordinationSpec.Termination term = team.coordination().termination();
            StudioCoordinationWriteRequest.StudioTerminationWriteRequest termination = term == null
                    ? null
                    : new StudioCoordinationWriteRequest.StudioTerminationWriteRequest(
                            term.maxIterations(), term.timeBudgetMs(), term.convergence(), term.decider());
            coordination = new StudioCoordinationWriteRequest(
                    team.coordination().pattern(), termination, team.coordination().acceptanceCriteria());
        }
        StudioLeadWriteRequest lead = team.lead() == null
                ? null
                : new StudioLeadWriteRequest(team.lead().name(), team.lead().title(), team.lead().avatar());
        StudioTeamAgentWriteRequest teamAgent = team.teamAgent() == null
                ? null
                : new StudioTeamAgentWriteRequest(
                        team.teamAgent().teamMode(),
                        team.teamAgent().spawnMode(),
                        team.teamAgent().teammateMode());
        List<StudioTeamMemberWriteRequest> members = team.members().stream()
                .map(member -> new StudioTeamMemberWriteRequest(
                        member.id(),
                        member.name(),
                        member.expertId(),
                        member.role(),
                        member.order(),
                        member.avatar(),
                        member.profession(),
                        member.backend().yamlValue(),
                        null))
                .toList();
        return new StudioTeamWriteRequest(
                team.id(),
                team.name(),
                team.description(),
                team.systemPrompt(),
                team.defaultInitPrompt(),
                team.category(),
                team.tags(),
                team.collaboration(),
                team.resolvedTeamRuntime(),
                coordination,
                lead,
                teamAgent,
                members,
                team.maxTurns());
    }

    private static StudioTeamWriteRequest copyTeam(
            StudioTeamWriteRequest base,
            StudioCoordinationWriteRequest coordination,
            String teamRuntime,
            StudioLeadWriteRequest lead,
            String promptContent,
            List<StudioTeamMemberWriteRequest> members) {
        return new StudioTeamWriteRequest(
                base.id(),
                base.name(),
                base.description(),
                promptContent != null ? promptContent : base.promptContent(),
                base.defaultInitPrompt(),
                base.category(),
                base.tags(),
                base.collaboration(),
                teamRuntime != null ? teamRuntime : base.teamRuntime(),
                coordination != null ? coordination : base.coordination(),
                lead != null ? lead : base.lead(),
                base.teamAgent(),
                members != null ? members : base.members(),
                base.maxTurns());
    }

    private StudioTeamViewResponse buildTeamView(ExpertRegistryEntry entry, ExpertDefinition team) {
        List<String> warnings = collectMemberWarnings(team);
        List<StudioTeamMemberView> members = team.members().stream()
                .map(member -> {
                    var memberEntry = expertRegistry.findEntry(member.expertId());
                    String promptFile = memberEntry.map(ExpertRegistryEntry::promptFile).orElse("prompt.md");
                    String promptContent = memberEntry
                            .map(memberExpert -> memberExpert.definition().systemPrompt())
                            .orElse("");
                    String expertYaml = memberEntry
                            .map(memberExpert -> ExpertYamlWriter.render(
                                    memberExpert.definition(), memberExpert.promptFile()))
                            .orElse("");
                    return new StudioTeamMemberView(
                            member,
                            memberEntry.isPresent(),
                            memberEntry.map(e -> e.source().name()).orElse("MISSING"),
                            promptFile,
                            promptContent,
                            expertYaml);
                })
                .toList();
        return new StudioTeamViewResponse(
                StudioExpertSourceResponse.from(entry),
                members,
                buildRuntimePreview(team),
                warnings);
    }

    private List<String> collectMemberWarnings(ExpertDefinition team) {
        List<String> warnings = new ArrayList<>();
        for (TeamMemberDefinition member : team.members()) {
            if (expertRegistry.findExpert(member.expertId()).isEmpty()) {
                warnings.add("Unresolved member expertId: " + member.expertId());
            }
        }
        CoordinationSpec coordination = team.coordination();
        if (coordination != null && coordination.hasLead() && team.lead() == null) {
            warnings.add("Topology " + coordination.pattern() + " expects a lead definition");
        }
        return List.copyOf(warnings);
    }

    private StudioRuntimePreviewResponse buildRuntimePreview(ExpertDefinition team) {
        TeamRuntimeKind resolved = teamRuntimeRouter.resolveKind(team);
        String requested = team.resolvedTeamRuntime();
        String pattern = team.coordinationPattern();
        boolean migratable = CoordinationSpec.ORCHESTRATOR.equals(pattern)
                || CoordinationSpec.AGENT_TEAM.equals(pattern);
        boolean hasLead = team.coordination() != null && team.coordination().hasLead();
        String hint;
        if (!migratable && TeamRuntimeKind.OPENJIUWEN_TEAM.equals(resolved)) {
            hint = "Only orchestrator/agent-team patterns migrate to openjiuwen-team; others use workmate-orchestrator";
        } else if (TeamRuntimeKind.OPENJIUWEN_TEAM.equals(resolved)) {
            hint = "TeamAgent runtime: leader-driven spawn/mailbox orchestration";
        } else {
            hint = "Workmate orchestrator: Java topology executors";
        }
        return new StudioRuntimePreviewResponse(
                requested,
                resolved.yamlValue(),
                pattern,
                migratable,
                hasLead,
                hint);
    }

    private void writeTeamDraft(NormalizedTeamWrite normalized) {
        draftStore.writeExpertDraft(
                normalized.teamId(),
                normalized.definition(),
                normalized.promptFile(),
                normalized.promptContent());
        for (MemberDraft member : normalized.memberDrafts()) {
            if (member.definition() != null) {
                draftStore.writeExpertDraft(
                        member.expertId(), member.definition(), "prompt.md", member.promptContent());
            }
        }
    }

    private NormalizedTeamWrite normalizeTeamRequest(
            StudioTeamWriteRequest request, String pathTeamId, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("Request body required");
        }
        String teamId = creating
                ? OfficeImportValidator.requireSafeId(request.id(), "Team")
                : OfficeImportValidator.requireSafeId(pathTeamId, "Team");
        String name = OfficeImportValidator.requireText(request.name(), "Team name");
        String description = OfficeImportValidator.requireText(request.description(), "Team description");
        String promptContent = OfficeImportValidator.requireText(request.promptContent(), "Lead prompt content");
        List<StudioTeamMemberWriteRequest> rawMembers =
                request.members() == null ? List.of() : List.copyOf(request.members());
        if (rawMembers.size() < 2) {
            throw new IllegalArgumentException("Team requires at least 2 members");
        }

        String collaboration = blankToDefault(request.collaboration(), "sequential");
        String category = blankToDefault(request.category(), "custom");
        List<String> tags = request.tags() == null ? List.of("draft", "team") : List.copyOf(request.tags());
        String defaultInitPrompt = request.defaultInitPrompt() == null ? "" : request.defaultInitPrompt().trim();
        CoordinationSpec coordination = parseCoordination(request.coordination(), collaboration);
        TeamLeadDefinition lead = parseLead(request.lead(), name, coordination);
        TeamAgentOverrides teamAgent = parseTeamAgent(request.teamAgent());
        String teamRuntime = request.teamRuntime() == null ? null : request.teamRuntime().trim();

        List<TeamMemberDefinition> members = new ArrayList<>();
        List<MemberDraft> memberDrafts = new ArrayList<>();
        int index = 0;
        for (StudioTeamMemberWriteRequest raw : rawMembers) {
            index++;
            String memberId = OfficeImportValidator.requireSafeId(raw.id(), "Team member");
            String expertId = raw.expertId() == null || raw.expertId().isBlank()
                    ? teamId + "__" + memberId
                    : OfficeImportValidator.requireSafeId(raw.expertId(), "Team member expertId");
            int order = raw.order() != null && raw.order() > 0 ? raw.order() : index;
            TeamMemberBackend backend = parseBackend(raw.backend());
            Map<String, String> profession =
                    raw.profession() == null ? Map.of() : Map.copyOf(raw.profession());
            String memberName = raw.name() == null || raw.name().isBlank() ? memberId : raw.name().trim();
            members.add(new TeamMemberDefinition(
                    memberId,
                    memberName,
                    expertId,
                    raw.role(),
                    order,
                    raw.avatar(),
                    null,
                    profession,
                    null,
                    backend,
                    null));
            MemberDraft memberDraft = resolveMemberDraft(teamId, memberId, expertId, memberName, raw);
            if (memberDraft != null) {
                memberDrafts.add(memberDraft);
            }
        }
        members.sort((a, b) -> Integer.compare(a.order(), b.order()));

        ExpertDefinition definition = new ExpertDefinition(
                teamId,
                name,
                description,
                "team",
                promptContent,
                defaultInitPrompt,
                category,
                tags,
                List.of(),
                members,
                collaboration,
                lead,
                coordination,
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                request.maxTurns(),
                List.of(),
                List.of(),
                teamRuntime,
                teamAgent);

        return new NormalizedTeamWrite(teamId, "lead-prompt.md", promptContent, definition, memberDrafts);
    }

    private static MemberDraft resolveMemberDraft(
            String teamId,
            String memberId,
            String expertId,
            String memberName,
            StudioTeamMemberWriteRequest raw) {
        if (expertId == null || expertId.isBlank()) {
            expertId = teamId + "__" + memberId;
        }
        // Only write sub-agent draft when prompt provided and expert not yet materialized as separate yaml in drafts
        String prompt = raw.promptContent();
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        ExpertDefinition subAgent = new ExpertDefinition(
                expertId,
                memberName,
                memberName + " member agent",
                "agent",
                prompt,
                "",
                "custom",
                List.of("team-member", "draft"),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                List.of(),
                List.of(),
                null,
                null);
        return new MemberDraft(expertId, subAgent, prompt);
    }

    private static CoordinationSpec parseCoordination(
            StudioCoordinationWriteRequest raw, String collaborationFallback) {
        String pattern = raw != null && raw.pattern() != null && !raw.pattern().isBlank()
                ? raw.pattern().trim()
                : ("parallel".equalsIgnoreCase(collaborationFallback)
                        ? CoordinationSpec.AGENT_TEAM
                        : CoordinationSpec.ORCHESTRATOR);
        CoordinationSpec.Termination termination = null;
        if (raw != null && raw.termination() != null) {
            StudioCoordinationWriteRequest.StudioTerminationWriteRequest term = raw.termination();
            CoordinationSpec.Termination parsed = new CoordinationSpec.Termination(
                    term.maxIterations(), term.timeBudgetMs(), term.convergence(), term.decider());
            if (!parsed.isEmpty()) {
                termination = parsed;
            }
        }
        String acceptanceCriteria = raw != null ? raw.acceptanceCriteria() : null;
        return new CoordinationSpec(pattern, termination, acceptanceCriteria);
    }

    private static TeamLeadDefinition parseLead(
            StudioLeadWriteRequest raw, String teamName, CoordinationSpec coordination) {
        if (coordination == null || !coordination.hasLead()) {
            return null;
        }
        if (raw == null || raw.name() == null || raw.name().isBlank()) {
            return TeamLeadDefinition.fallback(teamName);
        }
        Map<String, String> title = raw.title() == null ? Map.of() : Map.copyOf(raw.title());
        return new TeamLeadDefinition(raw.name().trim(), title, raw.avatar());
    }

    private static TeamAgentOverrides parseTeamAgent(StudioTeamAgentWriteRequest raw) {
        if (raw == null) {
            return null;
        }
        return new TeamAgentOverrides(raw.teamMode(), raw.spawnMode(), raw.teammateMode());
    }

    private static TeamMemberBackend parseBackend(String backend) {
        if (backend == null || backend.isBlank()) {
            return TeamMemberBackend.LOCAL;
        }
        try {
            return TeamMemberBackend.fromYaml(backend);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid member backend: " + backend);
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record NormalizedTeamWrite(
            String teamId,
            String promptFile,
            String promptContent,
            ExpertDefinition definition,
            List<MemberDraft> memberDrafts) {}

    private record MemberDraft(String expertId, ExpertDefinition definition, String promptContent) {}
}
