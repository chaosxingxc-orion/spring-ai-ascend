package com.huawei.ascend.examples.workmate.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.acp.AcpConverterFacade;
import com.huawei.ascend.examples.workmate.acp.AcpNdjsonParser;
import com.huawei.ascend.examples.workmate.acp.AcpStreamIngress;
import com.huawei.ascend.examples.workmate.acp.RunEventDraft;
import com.huawei.ascend.examples.workmate.agent.AgentRunService;
import com.huawei.ascend.examples.workmate.agent.RunStreamService;
import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.team.TeamSnapshotService;
import com.huawei.ascend.examples.workmate.team.dto.TeamSnapshotResponse;
import com.huawei.ascend.examples.workmate.session.SessionRunQueue;
import com.huawei.ascend.examples.workmate.session.dto.ClearRunQueueResponse;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionRequest;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionResponse;
import com.huawei.ascend.examples.workmate.session.dto.AutoArchiveRequest;
import com.huawei.ascend.examples.workmate.session.dto.AutoArchiveResponse;
import com.huawei.ascend.examples.workmate.session.dto.RunQueueStatusResponse;
import com.huawei.ascend.examples.workmate.session.dto.SessionLimitsResponse;
import com.huawei.ascend.examples.workmate.session.dto.ExpertTransitionRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionConnectorsRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionSkillsRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionMetadataRequest;
import com.huawei.ascend.examples.workmate.session.dto.SessionResponse;
import com.huawei.ascend.examples.workmate.session.dto.SessionSummaryResponse;
import com.huawei.ascend.examples.workmate.session.dto.UpdatePlanRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionPersistenceService sessionPersistenceService;
    private final RunStreamService runStreamService;
    private final TeamSnapshotService teamSnapshotService;
    private final AgentRunService agentRunService;
    private final AcpConverterFacade acpConverterFacade;
    private final AcpStreamIngress acpStreamIngress;
    private final AcpNdjsonParser acpNdjsonParser;
    private final ObjectMapper objectMapper;

    public SessionController(
            SessionService sessionService,
            SessionPersistenceService sessionPersistenceService,
            RunStreamService runStreamService,
            TeamSnapshotService teamSnapshotService,
            AgentRunService agentRunService,
            AcpConverterFacade acpConverterFacade,
            AcpStreamIngress acpStreamIngress,
            AcpNdjsonParser acpNdjsonParser,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.sessionPersistenceService = sessionPersistenceService;
        this.runStreamService = runStreamService;
        this.teamSnapshotService = teamSnapshotService;
        this.agentRunService = agentRunService;
        this.acpConverterFacade = acpConverterFacade;
        this.acpStreamIngress = acpStreamIngress;
        this.acpNdjsonParser = acpNdjsonParser;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSessionResponse createSession(@Valid @RequestBody(required = false) CreateSessionRequest request) {
        CreateSessionRequest payload = request == null
                ? new CreateSessionRequest(null, null, null, null, null, null, null, null, null, null)
                : request;
        return sessionService.createSession(payload);
    }

    @PostMapping("/auto-archive")
    public AutoArchiveResponse autoArchive(@Valid @RequestBody AutoArchiveRequest request) {
        return sessionService.autoArchive(request);
    }

    @GetMapping
    public List<SessionResponse> listSessions() {
        return sessionService.listSessions();
    }

    @GetMapping("/summary")
    public List<SessionSummaryResponse> listSessionSummaries() {
        return sessionService.listSessionSummaries();
    }

    @GetMapping("/limits")
    public SessionLimitsResponse sessionLimits() {
        return sessionService.sessionLimits();
    }

    @GetMapping("/{id}")
    public SessionResponse getSession(@PathVariable UUID id) {
        return sessionService.getSession(id);
    }

    @GetMapping("/{id}/messages")
    public List<Map<String, Object>> listMessages(@PathVariable UUID id) {
        sessionService.requireSession(id);
        return sessionPersistenceService.listMessages(id);
    }

    @GetMapping("/{id}/run-events")
    public List<Map<String, Object>> listRunEvents(
            @PathVariable UUID id,
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "after", required = false) Integer after) {
        sessionService.requireSession(id);
        // Incremental fetch (after=seq) returns only newer events; used by the client poller to avoid
        // re-downloading the whole history. ACP projection still operates on the full log.
        List<Map<String, Object>> eventLog = (after != null && !"acp".equalsIgnoreCase(view))
                ? sessionPersistenceService.listEventLog(id, after)
                : sessionPersistenceService.listEventLog(id);
        if ("acp".equalsIgnoreCase(view)) {
            return acpConverterFacade.toAcpEventLog(eventLog);
        }
        return eventLog;
    }

    /** W38 Phase 2 — dry-run ACP sessionUpdate[] → run_events drafts (chunk merge applied). */
    @PostMapping("/{id}/acp/convert")
    public List<Map<String, Object>> convertAcpUpdates(
            @PathVariable UUID id,
            @RequestBody List<Map<String, Object>> updates) {
        sessionService.requireSession(id);
        return acpConverterFacade.fromAcpStream(updates).stream()
                .map(this::draftToMap)
                .toList();
    }

    /** W38 Phase 3 — persist ACP sessionUpdate[] to run_events. */
    @PostMapping("/{id}/acp/ingest")
    public List<Map<String, Object>> ingestAcpUpdates(
            @PathVariable UUID id,
            @RequestBody List<Map<String, Object>> updates) {
        sessionService.requireSession(id);
        return acpStreamIngress.ingest(id, updates).stream()
                .map(this::recordedEventToMap)
                .toList();
    }

    /** W38 Phase 3 — NDJSON sidecar bridge (one sessionUpdate JSON object per line). */
    @PostMapping(value = "/{id}/acp/ingest/ndjson", consumes = "application/x-ndjson")
    public List<Map<String, Object>> ingestAcpNdjson(
            @PathVariable UUID id,
            @RequestBody String ndjson) {
        sessionService.requireSession(id);
        return acpStreamIngress.ingest(id, acpNdjsonParser.parse(ndjson)).stream()
                .map(this::recordedEventToMap)
                .toList();
    }

    private Map<String, Object> draftToMap(RunEventDraft draft) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", draft.eventName());
        row.put("data", draft.payload());
        return row;
    }

    private Map<String, Object> recordedEventToMap(RecordedRunEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", event.seq());
        row.put("name", event.eventName());
        row.put("data", readEventPayload(event.payloadJson()));
        return row;
    }

    private Map<String, Object> readEventPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @GetMapping("/{id}/team-snapshot")
    public TeamSnapshotResponse teamSnapshot(@PathVariable UUID id) {
        return teamSnapshotService.build(id);
    }

    @PatchMapping("/{id}/metadata")
    public SessionResponse updateMetadata(
            @PathVariable UUID id, @Valid @RequestBody SessionMetadataRequest request) {
        return sessionService.updateMetadata(id, request);
    }

    @PostMapping("/{id}/expert-transition")
    public SessionResponse expertTransition(
            @PathVariable UUID id, @Valid @RequestBody ExpertTransitionRequest request) {
        return sessionService.applyExpertTransition(id, request);
    }

    @PatchMapping("/{id}/connectors")
    public SessionResponse updateConnectors(
            @PathVariable UUID id, @Valid @RequestBody SessionConnectorsRequest request) {
        return sessionService.updateConnectors(id, request);
    }

    @PatchMapping("/{id}/skills")
    public SessionResponse updateSkills(
            @PathVariable UUID id, @Valid @RequestBody SessionSkillsRequest request) {
        return sessionService.updateSkills(id, request);
    }

    @GetMapping("/{id}/run-queue")
    public RunQueueStatusResponse runQueue(@PathVariable UUID id) {
        sessionService.requireSession(id);
        return new RunQueueStatusResponse(
                agentRunService.queueDepth(id), SessionRunQueue.MAX_QUEUE_SIZE);
    }

    @DeleteMapping("/{id}/run-queue")
    public ClearRunQueueResponse clearRunQueue(@PathVariable UUID id) {
        sessionService.requireSession(id);
        int cleared = agentRunService.clearQueue(id);
        return new ClearRunQueueResponse(cleared, agentRunService.queueDepth(id));
    }

    @GetMapping(value = "/{id}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable UUID id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return runStreamService.resumeStream(id, lastEventId);
    }

    @PostMapping("/{id}/plan/confirm")
    public SessionResponse confirmPlan(@PathVariable UUID id) {
        return sessionService.confirmPlan(id);
    }

    /** W37-B5 — edit plan steps before confirm (PLAN mode). */
    @PatchMapping("/{id}/plans/{planId}")
    public Map<String, Object> updatePlan(
            @PathVariable UUID id,
            @PathVariable String planId,
            @RequestBody UpdatePlanRequest request) {
        return sessionService.updatePlan(id, planId, request);
    }
}
