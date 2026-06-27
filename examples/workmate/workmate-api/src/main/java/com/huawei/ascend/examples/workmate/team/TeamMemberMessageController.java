package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.team.runtime.TeamRuntimeManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code @member} bypass API: route a message directly to a team member (or {@code @all} /
 * {@code @main}) of an in-flight team run, without going through the leader.
 *
 * <p>The message is delivered into the team run's mailbox, which wakes the target member worker;
 * the member's output streams to the UI through the live run's SSE pipeline. Only works while a
 * team run is active for the session.</p>
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/team")
public class TeamMemberMessageController {

    private final MemberBypassMessageService bypassMessageService;
    private final MemberHandbackIngestService handbackIngestService;

    public TeamMemberMessageController(
            MemberBypassMessageService bypassMessageService, MemberHandbackIngestService handbackIngestService) {
        this.bypassMessageService = bypassMessageService;
        this.handbackIngestService = handbackIngestService;
    }

    /** Routing token + body. {@code target} accepts {@code @member} / {@code @all} / {@code @main} / member id. */
    public record MemberMessageRequest(String target, String message, String summary) {}

    /**
     * Proactive remote handback ingest (A2A push webhook): structured member deliverable → leader inbox
     * + member-scoped {@code send_message} run_events for the team surface.
     */
    public record RemoteHandbackRequest(String memberId, String content, String summary, String to) {}

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendToMember(
            @PathVariable UUID sessionId, @RequestBody MemberMessageRequest request) {
        try {
            return bypassMessageService
                    .send(
                            sessionId,
                            new MemberBypassMessageService.Request(
                                    request == null ? null : request.target(),
                                    request == null ? null : request.message(),
                                    request == null ? null : request.summary()))
                    .map(result -> {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("target", result.target());
                        body.put("delivered", result.delivered());
                        body.put("broadcast", result.broadcast());
                        return ResponseEntity.accepted().body(body);
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "no active team run for session",
                                    "sessionId", sessionId.toString())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/handbacks")
    public ResponseEntity<Map<String, Object>> ingestRemoteHandback(
            @PathVariable UUID sessionId, @RequestBody RemoteHandbackRequest request) {
        try {
            return handbackIngestService
                    .ingest(sessionId, new MemberHandbackIngestService.Request(
                            request == null ? null : request.memberId(),
                            request == null ? null : request.content(),
                            request == null ? null : request.summary(),
                            request == null ? null : request.to()))
                    .map(result -> {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("memberId", result.memberId());
                        body.put("resolvedRecipient", result.resolvedRecipient());
                        body.put("toolCallId", result.toolCallId());
                        body.put("sequence", result.sequence());
                        body.put("summary", result.summary());
                        return ResponseEntity.accepted().body(body);
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "no active team run for session",
                                    "sessionId", sessionId.toString())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
