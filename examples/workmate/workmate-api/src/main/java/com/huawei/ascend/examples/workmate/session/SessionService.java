package com.huawei.ascend.examples.workmate.session;

import com.huawei.ascend.examples.workmate.config.WorkmateSessionProperties;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.model.ModelEffort;
import com.huawei.ascend.examples.workmate.office.ExpertService;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactLayoutService;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.session.dto.AutoArchiveRequest;
import com.huawei.ascend.examples.workmate.session.dto.AutoArchiveResponse;
import com.huawei.ascend.examples.workmate.session.dto.AutoArchivedSession;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionRequest;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionResponse;
import com.huawei.ascend.examples.workmate.session.dto.ExpertTransitionRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionConnectorsRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionSkillsRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionMetadataRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionLimitsResponse;
import com.huawei.ascend.examples.workmate.session.dto.SessionResponse;
import com.huawei.ascend.examples.workmate.session.dto.SessionSummaryResponse;
import com.huawei.ascend.examples.workmate.session.dto.UpdatePlanRequest;
import com.huawei.ascend.examples.workmate.agent.RunEventBroadcaster;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.usage.SessionUsageService;
import java.util.LinkedHashMap;
import com.huawei.ascend.examples.workmate.usage.SessionUsageTotals;
import com.huawei.ascend.examples.workmate.taskstarter.GitRepoService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final String DEFAULT_TITLE = "Untitled task";

    private final WorkmateSessionRepository repository;
    private final WorkspaceService workspaceService;
    private final ExpertService expertService;
    private final SessionUsageService sessionUsageService;
    private final SessionPersistenceService sessionPersistenceService;
    private final OfficeArtifactLayoutService officeArtifactLayoutService;
    private final ModelCatalogService modelCatalogService;
    private final WorkmateSessionProperties sessionProperties;
    private final SessionAutoArchiveService sessionAutoArchiveService;
    private final GitRepoService gitRepoService;
    private final AuditLedgerService auditLedgerService;
    private final RunEventBroadcaster runEventBroadcaster;
    private final ExpertHandoffService expertHandoffService;

    public SessionService(
            WorkmateSessionRepository repository,
            WorkspaceService workspaceService,
            ExpertService expertService,
            SessionUsageService sessionUsageService,
            SessionPersistenceService sessionPersistenceService,
            OfficeArtifactLayoutService officeArtifactLayoutService,
            ModelCatalogService modelCatalogService,
            WorkmateSessionProperties sessionProperties,
            SessionAutoArchiveService sessionAutoArchiveService,
            GitRepoService gitRepoService,
            AuditLedgerService auditLedgerService,
            RunEventBroadcaster runEventBroadcaster,
            ExpertHandoffService expertHandoffService) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.expertService = expertService;
        this.sessionUsageService = sessionUsageService;
        this.sessionPersistenceService = sessionPersistenceService;
        this.officeArtifactLayoutService = officeArtifactLayoutService;
        this.modelCatalogService = modelCatalogService;
        this.sessionProperties = sessionProperties;
        this.sessionAutoArchiveService = sessionAutoArchiveService;
        this.gitRepoService = gitRepoService;
        this.auditLedgerService = auditLedgerService;
        this.runEventBroadcaster = runEventBroadcaster;
        this.expertHandoffService = expertHandoffService;
    }

    @Transactional(readOnly = true)
    public SessionLimitsResponse sessionLimits() {
        int maxActive = sessionProperties.maxActive();
        long activeCount = repository.countByArchivedAtIsNull();
        return new SessionLimitsResponse(
                (int) activeCount,
                maxActive,
                sessionProperties.autoArchiveOnCreate(),
                sessionAutoArchiveService.countArchivableCandidates());
    }

    @Transactional
    public CreateSessionResponse createSession(CreateSessionRequest request) {
        List<AutoArchivedSession> autoArchived = ensureCapacityForNewSession(request);
        UUID sessionId = UUID.randomUUID();
        Path workspaceRoot = resolveWorkspaceRoot(sessionId, request.workspacePath());
        workspaceService.createWorkspace(workspaceRoot);
        prepareGitWorkspace(request.workspacePath(), request.gitBranch());

        String title = normalizeTitle(request.title());
        String expertId = normalizeExpertId(request.expertId());
        ExpertDefinition expertDefinition = null;
        if (expertId != null) {
            expertDefinition = expertService.requireExpertDefinition(expertId);
            officeArtifactLayoutService.bootstrapLayout(workspaceRoot, expertDefinition, sessionId);
        }
        WorkmateSession session = new WorkmateSession(
                sessionId,
                title,
                workspaceRoot.toString(),
                SessionStatus.CREATED,
                expertId,
                request.permissionMode() != null ? request.permissionMode() : PermissionMode.CRAFT);
        if (request.modelId() != null && !request.modelId().isBlank()) {
            session.setModelId(modelCatalogService.normalizeModelId(request.modelId()));
            modelCatalogService.resolve(session.getModelId());
        }
        if (request.effort() != null && !request.effort().isBlank()) {
            session.setEffort(ModelEffort.parse(request.effort()).name());
        }
        if (request.enabledConnectorIds() != null) {
            session.setEnabledConnectorIds(request.enabledConnectorIds());
        }
        if (request.enabledSkillIds() != null) {
            session.setEnabledSkillIds(request.enabledSkillIds());
        }
        return new CreateSessionResponse(toResponse(repository.save(session)), autoArchived);
    }

    @Transactional
    public AutoArchiveResponse autoArchive(AutoArchiveRequest request) {
        if (request == null
                || (request.count() == null && request.targetActiveCount() == null)
                || (request.count() != null && request.targetActiveCount() != null)) {
            throw new IllegalArgumentException("Provide exactly one of count or targetActiveCount");
        }
        List<WorkmateSession> archived;
        if (request.count() != null) {
            archived = sessionAutoArchiveService.archiveOldest(request.count());
        } else {
            archived = sessionAutoArchiveService.archiveToTargetActiveCount(request.targetActiveCount());
        }
        int maxActive = sessionProperties.maxActive();
        long activeCount = repository.countByArchivedAtIsNull();
        return new AutoArchiveResponse(toAutoArchived(archived), (int) activeCount, maxActive);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Sidebar task list — single table scan, no per-session usage or expert lookups. */
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> listSessionSummaries() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(session -> SessionSummaryResponse.from(
                        session, workspaceService.workspaceKey(session.getWorkspaceRoot())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID id) {
        return toResponse(requireSession(id));
    }

    @Transactional(readOnly = true)
    public WorkmateSession requireSession(UUID id) {
        return repository.findById(id).orElseThrow(() -> new SessionNotFoundException(id));
    }

    @Transactional
    public void updateStatus(UUID id, SessionStatus status) {
        WorkmateSession session = requireSession(id);
        session.setStatus(status);
        repository.save(session);
    }

    @Transactional
    public Map<String, Object> updatePlan(UUID id, String planId, UpdatePlanRequest request) {
        requireSession(id);
        if (request == null || request.steps() == null || request.steps().isEmpty()) {
            throw new IllegalArgumentException("steps are required");
        }
        SessionPersistenceService.PlanUpdateResult result =
                sessionPersistenceService.updatePlan(id, planId, request.title(), request.steps());
        if (result.changed()) {
            RunPersistenceContext context = RunPersistenceContext.forAudit(id, result.runId());
            RecordedRunEvent recorded =
                    auditLedgerService.record(context, "plan.update", planEventPayload(result.plan()));
            if (recorded != null) {
                runEventBroadcaster.publish(id, result.runId(), recorded);
            }
        }
        return result.plan();
    }

    private static Map<String, Object> planEventPayload(Map<String, Object> plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan.get("planId"));
        if (plan.get("title") != null) {
            payload.put("title", plan.get("title"));
        }
        payload.put("steps", plan.get("steps"));
        return payload;
    }

    @Transactional
    public SessionResponse confirmPlan(UUID id) {
        WorkmateSession session = requireSession(id);
        if (session.getPermissionMode() != PermissionMode.PLAN) {
            throw new IllegalStateException("Session is not in PLAN mode");
        }
        session.setPermissionMode(PermissionMode.CRAFT);
        session.setPermissionModeBeforePlan(null);
        sessionPersistenceService.confirmPlan(id, null);
        return toResponse(repository.save(session));
    }

    @Transactional
    public SessionResponse updateMetadata(UUID id, SessionMetadataRequest request) {
        if (request == null
                || (request.pinned() == null
                        && request.archived() == null
                        && request.modelId() == null
                        && request.effort() == null
                        && request.enabledConnectorIds() == null
                        && request.enabledSkillIds() == null
                        && request.permissionMode() == null
                        && request.expertId() == null)) {
            throw new IllegalArgumentException("At least one metadata field is required");
        }
        WorkmateSession session = requireSession(id);
        if (request.pinned() != null) {
            if (session.getArchivedAt() != null && Boolean.TRUE.equals(request.pinned())) {
                throw new IllegalStateException("Archived sessions cannot be pinned");
            }
            session.setPinned(request.pinned());
        }
        if (request.archived() != null) {
            if (request.archived()) {
                session.setArchivedAt(Instant.now());
                session.setPinned(false);
            } else {
                session.setArchivedAt(null);
            }
        }
        if (request.modelId() != null) {
            if (request.modelId().isBlank()) {
                session.setModelId(null);
            } else {
                String normalized = modelCatalogService.normalizeModelId(request.modelId());
                modelCatalogService.resolve(normalized);
                session.setModelId(normalized);
            }
        }
        if (request.effort() != null) {
            if (request.effort().isBlank()) {
                session.setEffort(null);
            } else {
                session.setEffort(ModelEffort.parse(request.effort()).name());
            }
        }
        if (request.enabledConnectorIds() != null) {
            session.setEnabledConnectorIds(request.enabledConnectorIds());
        }
        if (request.enabledSkillIds() != null) {
            session.setEnabledSkillIds(request.enabledSkillIds());
        }
        if (request.permissionMode() != null) {
            SessionPermissionModeTransition.apply(session, request.permissionMode());
        }
        if (request.expertId() != null) {
            applyExpertChange(session, request.expertId(), ExpertTransitionMode.SUMMON_IN_SESSION);
        }
        return toResponse(repository.save(session));
    }

    @Transactional
    public SessionResponse applyExpertTransition(UUID id, ExpertTransitionRequest request) {
        if (request == null || request.expertId() == null || request.expertId().isBlank()) {
            throw new IllegalArgumentException("expertId is required");
        }
        WorkmateSession session = requireSession(id);
        ExpertTransitionMode mode =
                request.mode() != null ? request.mode() : ExpertTransitionMode.SUMMON_IN_SESSION;
        if (mode != ExpertTransitionMode.SUMMON_IN_SESSION) {
            throw new IllegalArgumentException("Only SUMMON_IN_SESSION is supported on this endpoint");
        }
        if (request.enabledConnectorIds() != null) {
            session.setEnabledConnectorIds(request.enabledConnectorIds());
        }
        applyExpertChange(session, request.expertId(), mode);
        return toResponse(repository.save(session));
    }

    @Transactional
    public void clearPendingHandoff(UUID sessionId) {
        WorkmateSession session = requireSession(sessionId);
        session.setPendingHandoffGeneration(null);
        session.setPendingHandoffFromExpertId(null);
        repository.save(session);
    }

    private void applyExpertChange(
            WorkmateSession session, String rawExpertId, ExpertTransitionMode mode) {
        if (session.getArchivedAt() != null) {
            throw new IllegalStateException("Archived sessions cannot change expert");
        }
        if (session.getStatus() == SessionStatus.RUNNING) {
            throw new IllegalStateException("Cannot switch expert while the session is running");
        }
        String expertId = normalizeExpertId(rawExpertId);
        if (expertId == null) {
            session.setExpertId(null);
            return;
        }
        if (expertId.equals(session.getExpertId())) {
            return;
        }
        String fromExpertId = session.getExpertId();
        ExpertDefinition expertDefinition = expertService.requireExpertDefinition(expertId);
        Path workspaceRoot = Path.of(session.getWorkspaceRoot());
        officeArtifactLayoutService.bootstrapLayout(workspaceRoot, expertDefinition, session.getId());
        int newGeneration = session.getConversationGeneration() + 1;
        session.setConversationGeneration(newGeneration);
        session.setExpertId(expertId);
        if (mode == ExpertTransitionMode.SUMMON_IN_SESSION) {
            session.setPendingHandoffGeneration(newGeneration);
            session.setPendingHandoffFromExpertId(fromExpertId);
            recordExpertSwitch(session, fromExpertId, expertDefinition, newGeneration, mode);
        }
    }

    private void recordExpertSwitch(
            WorkmateSession session,
            String fromExpertId,
            ExpertDefinition toExpert,
            int newGeneration,
            ExpertTransitionMode mode) {
        UUID sessionId = session.getId();
        String auditRunId = UUID.randomUUID().toString();
        RunPersistenceContext context = RunPersistenceContext.forAudit(sessionId, auditRunId);
        Map<String, Object> payload = new LinkedHashMap<>(
                expertHandoffService.switchedEventPayload(
                        fromExpertId, toExpert.id(), newGeneration, mode.name()));
        sessionPersistenceService.recordExpertSwitched(context, payload);
        RecordedRunEvent recorded = auditLedgerService.record(context, "expert.switched", payload);
        if (recorded != null) {
            runEventBroadcaster.publish(sessionId, auditRunId, recorded);
        }
    }

    @Transactional
    public SessionResponse updateConnectors(UUID id, SessionConnectorsRequest request) {
        if (request == null || request.enabledConnectorIds() == null) {
            throw new IllegalArgumentException("enabledConnectorIds is required");
        }
        WorkmateSession session = requireSession(id);
        session.setEnabledConnectorIds(request.enabledConnectorIds());
        return toResponse(repository.save(session));
    }

    @Transactional
    public SessionResponse updateSkills(UUID id, SessionSkillsRequest request) {
        if (request == null || request.enabledSkillIds() == null) {
            throw new IllegalArgumentException("enabledSkillIds is required");
        }
        WorkmateSession session = requireSession(id);
        session.setEnabledSkillIds(request.enabledSkillIds());
        return toResponse(repository.save(session));
    }

    private SessionResponse toResponse(WorkmateSession session) {
        SessionUsageTotals usage = sessionUsageService.totalsForSession(session.getId());
        String officeArtifactRoot = resolveOfficeArtifactRoot(session);
        return SessionResponse.from(session, workspaceService.workspaceKey(session.getWorkspaceRoot()), usage, officeArtifactRoot);
    }

    private String resolveOfficeArtifactRoot(WorkmateSession session) {
        if (session.getExpertId() == null || session.getExpertId().isBlank()) {
            return null;
        }
        return expertService.findExpertDefinition(session.getExpertId())
                .map(expert -> officeArtifactLayoutService.taskRootRelative(expert, session.getId()))
                .orElse(null);
    }

    private Path resolveWorkspaceRoot(UUID sessionId, String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return workspaceService.resolveSessionRoot(sessionId);
        }
        Path relative = Path.of(workspacePath).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new WorkspaceException("workspacePath must be a relative name under the workspace base");
        }
        return workspaceService.basePath().resolve(relative);
    }

    private void prepareGitWorkspace(String workspacePath, String gitBranch) {
        if (workspacePath == null || workspacePath.isBlank() || gitBranch == null || gitBranch.isBlank()) {
            return;
        }
        gitRepoService.checkoutBranch(workspacePath, gitBranch);
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        return title.trim();
    }

    private static String normalizeExpertId(String expertId) {
        if (expertId == null || expertId.isBlank()) {
            return null;
        }
        return expertId.trim();
    }

    private List<AutoArchivedSession> ensureCapacityForNewSession(CreateSessionRequest request) {
        int maxActive = sessionProperties.maxActive();
        long activeCount = repository.countByArchivedAtIsNull();
        int needed = SessionAutoArchiveService.slotsNeeded(activeCount, maxActive);
        if (needed <= 0) {
            return List.of();
        }
        if (!resolveAutoArchive(request)) {
            throw new SessionLimitExceededException((int) activeCount, maxActive);
        }
        return toAutoArchived(sessionAutoArchiveService.archiveOldest(needed));
    }

    private boolean resolveAutoArchive(CreateSessionRequest request) {
        if (request != null && request.autoArchive() != null) {
            return request.autoArchive();
        }
        return sessionProperties.autoArchiveOnCreate();
    }

    private static List<AutoArchivedSession> toAutoArchived(List<WorkmateSession> sessions) {
        return sessions.stream()
                .map(session -> new AutoArchivedSession(session.getId(), session.getTitle(), session.getArchivedAt()))
                .toList();
    }
}
