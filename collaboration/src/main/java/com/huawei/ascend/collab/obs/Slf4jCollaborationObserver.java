package com.huawei.ascend.collab.obs;

import com.huawei.ascend.collab.core.CollaborationObserver;
import com.huawei.ascend.collab.core.CoordinationEvent;
import com.huawei.ascend.collab.core.CoordinationEvent.Type;
import com.huawei.ascend.collab.core.WorkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Structured ops trail for the collaboration engine, on the {@code collab} logger with MDC
 * context ({@code taskId}/{@code event}/{@code outcome}/{@code workerId}/{@code durationMs}),
 * so a log pipeline can index and correlate a whole collaboration by task id.
 *
 * <p><b>Dev-time vs runtime.</b> One instrumentation surface, intensity switched by the
 * {@code verbose} flag + the logger level — not two code paths:
 * <ul>
 *   <li><b>routine</b> decisions (dispatch/validate-ok/complete/reclaim/handover/token-reject/
 *       validate-fail/input-required) → DEBUG by default (lean runtime: at fleet scale these are
 *       many per task), or INFO when {@code verbose} (dev: see everything without flipping levels).
 *       Always guarded by {@code isEnabled} so when the level is off there is no MDC churn or
 *       string building — near-zero hot-path cost.</li>
 *   <li><b>problems</b> (fail / no-worker) → WARN always (ops must see these even in prod).</li>
 *   <li><b>task settled</b> → one INFO (or WARN if not COMPLETED) per task — the canonical record,
 *       bounded at one line per task.</li>
 * </ul>
 *
 * <p>MDC keys are always removed in a {@code finally} block — a logging observer must not leak
 * context onto the worker/pool thread it runs on.
 */
public final class Slf4jCollaborationObserver implements CollaborationObserver {

    private static final Logger log = LoggerFactory.getLogger("collab");

    private final boolean verbose;

    /** Runtime-lean by default: routine decisions at DEBUG. */
    public Slf4jCollaborationObserver() {
        this(false);
    }

    public Slf4jCollaborationObserver(boolean verbose) {
        this.verbose = verbose;
    }

    /** Dev-time: routine decisions promoted to INFO so they show without enabling DEBUG. */
    public static Slf4jCollaborationObserver verbose() {
        return new Slf4jCollaborationObserver(true);
    }

    @Override
    public void onEvent(CoordinationEvent event) {
        if (isProblem(event.type())) {
            if (log.isWarnEnabled()) {
                logEvent(event, true);
            }
            return;
        }
        // Routine decision: INFO in verbose (dev), DEBUG otherwise (runtime). Guard so a
        // disabled level costs nothing — no MDC, no message building.
        if (verbose ? log.isInfoEnabled() : log.isDebugEnabled()) {
            logEvent(event, false);
        }
    }

    private void logEvent(CoordinationEvent event, boolean problem) {
        MDC.put("taskId", event.taskId());
        MDC.put("event", event.type().name());
        if (event.workerId() != null) {
            MDC.put("workerId", event.workerId());
        }
        try {
            if (problem) {
                log.warn("collab decision type={} task={} worker={} detail={}",
                        event.type(), event.taskId(), event.workerId(), event.detail());
            } else if (verbose) {
                log.info("collab decision type={} task={} worker={} detail={}",
                        event.type(), event.taskId(), event.workerId(), event.detail());
            } else {
                log.debug("collab decision type={} task={} worker={} detail={}",
                        event.type(), event.taskId(), event.workerId(), event.detail());
            }
        } finally {
            MDC.remove("taskId");
            MDC.remove("event");
            MDC.remove("workerId");
        }
    }

    @Override
    public void onTaskCompleted(String taskId, WorkResult.Status status, long durationMs) {
        boolean ok = status == WorkResult.Status.COMPLETED;
        if (ok ? !log.isInfoEnabled() : !log.isWarnEnabled()) {
            return;
        }
        MDC.put("taskId", taskId);
        MDC.put("outcome", status.name());
        MDC.put("durationMs", Long.toString(durationMs));
        try {
            if (ok) {
                log.info("collab task settled task={} outcome={} durationMs={}", taskId, status, durationMs);
            } else {
                log.warn("collab task settled task={} outcome={} durationMs={}", taskId, status, durationMs);
            }
        } finally {
            MDC.remove("taskId");
            MDC.remove("outcome");
            MDC.remove("durationMs");
        }
    }

    private static boolean isProblem(Type type) {
        return type == Type.FAIL || type == Type.NO_WORKER;
    }
}
