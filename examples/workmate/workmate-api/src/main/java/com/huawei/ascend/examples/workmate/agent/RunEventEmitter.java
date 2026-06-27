package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.acp.TeamSurfaceEnricher;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.examples.workmate.chat.RunEventTopicResolver;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RunEventEmitter {

    private final AuditLedgerService auditLedgerService;
    private final RunEventBroadcaster runEventBroadcaster;
    private final SseRunEventMapper eventMapper;

    public RunEventEmitter(
            AuditLedgerService auditLedgerService,
            RunEventBroadcaster runEventBroadcaster,
            SseRunEventMapper eventMapper) {
        this.auditLedgerService = auditLedgerService;
        this.runEventBroadcaster = runEventBroadcaster;
        this.eventMapper = eventMapper;
    }

    public void emit(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            String eventName,
            Map<String, Object> payload) {
        // Serialize per-run emits: with async team runs the leader stream and member workers
        // write to the same SSE stream concurrently. A single SSE stream must be written
        // sequentially, and seq assignment (audit ledger) must stay ordered, so we lock on the
        // per-run emitter (or this emitter when there is no emitter).
        Object lock = emitter != null ? emitter : this;
        synchronized (lock) {
            Map<String, Object> enriched = teamAwarePayload(context, new LinkedHashMap<>(payload));
            enriched.putIfAbsent(RunEventTopicResolver.TOPIC_FIELD, RunEventTopicResolver.resolve(eventName));
            // Per-turn segmentation of the leader stream (reference-style one bubble per turn). This is
            // leader-scoped: member sub-runs (context.memberId != null) and member deltas republished on
            // the parent stream (top-level memberId in payload) render on the team surface and must not be
            // tagged. We lazily open a turn id on the first leader delta and close it whenever the leader
            // fires a tool (build_team / send_message / workspace tools), so each contiguous narration
            // becomes its own bubble. For single-agent runs the turn id is already opened (and persisted)
            // by appendAssistantDelta; here we only ensure it is present and reused.
            boolean leaderScoped = context != null
                    && context.memberId() == null
                    && enriched.get("memberId") == null;
            if (leaderScoped && "message.delta".equals(eventName)) {
                if (context.assistantMessageId() == null) {
                    context.beginAssistantTurn(UUID.randomUUID().toString());
                }
                enriched.putIfAbsent("messageId", context.assistantMessageId());
            } else if (leaderScoped
                    && ("tool.start".equals(eventName)
                            || "team.build.completed".equals(eventName)
                            || "question.required".equals(eventName)
                            || "approval.required".equals(eventName))) {
                // A user-confirmation interruption ends the current narration just like a tool call:
                // close the turn so post-confirmation narration opens a fresh bubble (rendered after
                // the question/approval card) instead of being appended to the pre-confirmation text.
                context.closeAssistantTurn();
            }
            RecordedRunEvent recorded = auditLedgerService.record(context, eventName, enriched);
            if (recorded != null) {
                runEventBroadcaster.publish(context.sessionId(), context.runId(), recorded);
            }
            if (!clientConnected.get()) {
                return;
            }
            Integer seq = recorded != null ? recorded.seq() : null;
            if (!AgentRunService.trySend(emitter, eventMapper.sseEvent(eventName, enriched, seq))) {
                clientConnected.set(false);
            }
        }
    }

    private static Map<String, Object> teamAwarePayload(
            RunPersistenceContext context, Map<String, Object> payload) {
        return TeamSurfaceEnricher.enrich(context, payload);
    }
}
