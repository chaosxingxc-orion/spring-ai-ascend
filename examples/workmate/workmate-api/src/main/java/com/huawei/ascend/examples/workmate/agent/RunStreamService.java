package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.session.SessionNotFoundException;
import com.huawei.ascend.examples.workmate.session.SessionRunLock;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RunStreamService {

    private static final Logger LOG = LoggerFactory.getLogger(RunStreamService.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final SessionService sessionService;
    private final SessionPersistenceService sessionPersistenceService;
    private final SseRunEventMapper eventMapper;
    private final RunEventBroadcaster runEventBroadcaster;
    private final SessionRunLock sessionRunLock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final java.util.concurrent.ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "workmate-sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    public RunStreamService(
            SessionService sessionService,
            SessionPersistenceService sessionPersistenceService,
            SseRunEventMapper eventMapper,
            RunEventBroadcaster runEventBroadcaster,
            SessionRunLock sessionRunLock) {
        this.sessionService = sessionService;
        this.sessionPersistenceService = sessionPersistenceService;
        this.eventMapper = eventMapper;
        this.runEventBroadcaster = runEventBroadcaster;
        this.sessionRunLock = sessionRunLock;
    }

    public SseEmitter resumeStream(UUID sessionId, String lastEventId) {
        sessionService.requireSession(sessionId);
        int afterSeq = parseLastEventId(lastEventId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        executor.submit(() -> streamEventsAsync(sessionId, afterSeq, emitter));
        return emitter;
    }

    private void streamEventsAsync(UUID sessionId, int afterSeq, SseEmitter emitter) {
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter);
        AtomicBoolean clientConnected = new AtomicBoolean(true);
        emitter.onCompletion(() -> clientConnected.set(false));
        emitter.onTimeout(() -> clientConnected.set(false));
        emitter.onError(ex -> clientConnected.set(false));

        try {
            List<RecordedRunEvent> replay = sessionPersistenceService.listEventsAfter(sessionId, afterSeq);
            int lastSentSeq = afterSeq;
            for (RecordedRunEvent event : replay) {
                if (!clientConnected.get()) {
                    return;
                }
                if (!AgentRunService.trySend(emitter, eventMapper.fromRecordedEvent(event))) {
                    return;
                }
                lastSentSeq = event.seq();
                if (RunEventTerminal.isTerminal(event.eventName(), event.payloadJson())) {
                    emitter.complete();
                    return;
                }
            }

            WorkmateSession session = sessionService.requireSession(sessionId);
            if (!isLiveRun(sessionId, session)) {
                emitter.complete();
                return;
            }

            String runId = runEventBroadcaster.latestRunId(sessionId);
            if (runId == null) {
                emitter.complete();
                return;
            }

            int tailFromSeq = lastSentSeq;
            AutoCloseable subscription = runEventBroadcaster.subscribe(
                    sessionId, runId, event -> {
                        if (!clientConnected.get() || event.seq() <= tailFromSeq) {
                            return;
                        }
                        if (!AgentRunService.trySend(emitter, eventMapper.fromRecordedEvent(event))) {
                            clientConnected.set(false);
                            return;
                        }
                        if (isTerminal(event.eventName(), event.payloadJson())) {
                            emitter.complete();
                        }
                    });
            try {
                while (clientConnected.get() && isLiveRun(sessionId, sessionService.requireSession(sessionId))) {
                    Thread.sleep(500);
                }
                if (clientConnected.get()) {
                    emitter.complete();
                }
            } finally {
                try {
                    subscription.close();
                } catch (Exception ignored) {
                    // unsubscribe
                }
            }
        } catch (SessionNotFoundException ex) {
            AgentRunService.trySend(emitter, eventMapper.runFailed(ex.getMessage()));
            emitter.complete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException ex) {
            LOG.warn("SSE resume failed sessionId={}", sessionId, ex);
            emitter.completeWithError(ex);
        } finally {
            heartbeat.cancel(true);
        }
    }

    static int parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(lastEventId.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    static boolean isTerminal(String eventName) {
        return isTerminal(eventName, null);
    }

    static boolean isTerminal(String eventName, String payloadJson) {
        return RunEventTerminal.isTerminal(eventName, payloadJson);
    }

    private boolean isLiveRun(UUID sessionId, WorkmateSession session) {
        if (session.getStatus() == SessionStatus.RUNNING || sessionRunLock.isLocked(sessionId)) {
            return true;
        }
        String runId = runEventBroadcaster.latestRunId(sessionId);
        return runId != null && runEventBroadcaster.isRunActive(sessionId, runId);
    }

    ScheduledFuture<?> startHeartbeat(SseEmitter emitter) {
        return heartbeatScheduler.scheduleAtFixedRate(
                () -> AgentRunService.trySend(emitter, eventMapper.heartbeat()),
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }
}
