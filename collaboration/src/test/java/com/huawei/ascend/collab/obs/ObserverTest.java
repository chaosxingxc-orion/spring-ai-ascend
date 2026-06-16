package com.huawei.ascend.collab.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.huawei.ascend.collab.core.CollaborationObserver;
import com.huawei.ascend.collab.core.CoordinationEvent;
import com.huawei.ascend.collab.core.CoordinationEvent.Type;
import com.huawei.ascend.collab.core.WorkResult.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Composite fan-out (incl. fault isolation), and the structured observer's dev/runtime
 * level routing + MDC leak-safety. Levels are asserted via a logback {@link ListAppender}.
 */
class ObserverTest {

    private static final class Recording implements CollaborationObserver {
        final List<String> events = new ArrayList<>();
        final List<String> completions = new ArrayList<>();

        @Override
        public void onEvent(CoordinationEvent e) {
            events.add(e.type().name());
        }

        @Override
        public void onTaskCompleted(String taskId, Status status, long durationMs) {
            completions.add(taskId + ":" + status);
        }
    }

    @Test
    void compositeFansOutToEveryDelegate() {
        Recording a = new Recording();
        Recording b = new Recording();
        CompositeCollaborationObserver obs = CompositeCollaborationObserver.of(a, b);

        obs.onEvent(new CoordinationEvent("t1", Type.DISPATCH, "w", null));
        obs.onTaskCompleted("t1", Status.COMPLETED, 5);

        assertEquals(List.of("DISPATCH"), a.events);
        assertEquals(List.of("DISPATCH"), b.events);
        assertEquals(List.of("t1:COMPLETED"), a.completions);
        assertEquals(List.of("t1:COMPLETED"), b.completions);
    }

    @Test
    void aFailingDelegateDoesNotBreakTheOthers() {
        CollaborationObserver boom = new CollaborationObserver() {
            @Override
            public void onEvent(CoordinationEvent e) {
                throw new RuntimeException("boom");
            }

            @Override
            public void onTaskCompleted(String t, Status s, long d) {
                throw new RuntimeException("boom");
            }
        };
        Recording good = new Recording();
        CompositeCollaborationObserver obs = CompositeCollaborationObserver.of(boom, good);

        obs.onEvent(new CoordinationEvent("t1", Type.FAIL, "w", null));
        obs.onTaskCompleted("t1", Status.FAILED, 1);

        assertEquals(List.of("FAIL"), good.events, "the healthy delegate still observed");
        assertEquals(List.of("t1:FAILED"), good.completions);
    }

    /** Captures everything logged on the {@code collab} logger at the given level, for assertions. */
    private static ListAppender<ILoggingEvent> capture(Level level) {
        ch.qos.logback.classic.Logger collab =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("collab");
        collab.setLevel(level);
        collab.setAdditive(false); // don't also spam the console appender during the test
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        collab.addAppender(app);
        return app;
    }

    private static void detach(ListAppender<ILoggingEvent> app) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("collab")).detachAppender(app);
    }

    private static Level levelOf(ListAppender<ILoggingEvent> app, String contains) {
        return app.list.stream()
                .filter(e -> e.getFormattedMessage().contains(contains))
                .map(ILoggingEvent::getLevel)
                .findFirst().orElse(null);
    }

    @Test
    void runtimeLeanRoutesRoutineToDebugProblemsToWarn() {
        ListAppender<ILoggingEvent> app = capture(Level.TRACE);
        try {
            Slf4jCollaborationObserver obs = new Slf4jCollaborationObserver(); // runtime-lean
            obs.onEvent(new CoordinationEvent("t1", Type.DISPATCH, "w", null));   // routine
            obs.onEvent(new CoordinationEvent("t1", Type.RECLAIM, "w", null));    // routine
            obs.onEvent(new CoordinationEvent("t1", Type.FAIL, "w", null));       // problem
            obs.onEvent(new CoordinationEvent("t2", Type.NO_WORKER, null, null)); // problem
            obs.onTaskCompleted("t1", Status.COMPLETED, 5);                       // settled ok
            obs.onTaskCompleted("t2", Status.FAILED, 3);                          // settled bad

            assertEquals(Level.DEBUG, levelOf(app, "type=DISPATCH"), "routine → DEBUG at runtime");
            assertEquals(Level.DEBUG, levelOf(app, "type=RECLAIM"), "routine → DEBUG at runtime");
            assertEquals(Level.WARN, levelOf(app, "type=FAIL"), "problem → WARN");
            assertEquals(Level.WARN, levelOf(app, "type=NO_WORKER"), "problem → WARN");
            assertEquals(Level.INFO, levelOf(app, "outcome=COMPLETED"), "settled ok → INFO");
            assertEquals(Level.WARN, levelOf(app, "outcome=FAILED"), "settled non-ok → WARN");
        } finally {
            detach(app);
        }
    }

    @Test
    void hotPathDoesNoWorkWhenItsLevelIsOff() {
        // Runtime prod default: level at INFO → routine DEBUG events must not be emitted
        // (and, being guarded, must not even touch MDC).
        ListAppender<ILoggingEvent> app = capture(Level.INFO);
        try {
            new Slf4jCollaborationObserver().onEvent(new CoordinationEvent("t1", Type.DISPATCH, "w", "d"));
            assertTrue(app.list.isEmpty(), "routine decision is silent when DEBUG is off");
            assertNull(MDC.get("taskId"), "guarded: no MDC churn when the level is off");
        } finally {
            detach(app);
        }
    }

    @Test
    void verboseDevModePromotesRoutineToInfo() {
        ListAppender<ILoggingEvent> app = capture(Level.INFO);
        try {
            Slf4jCollaborationObserver.verbose().onEvent(new CoordinationEvent("t1", Type.DISPATCH, "w", null));
            assertEquals(Level.INFO, levelOf(app, "type=DISPATCH"), "verbose dev mode → routine at INFO");
        } finally {
            detach(app);
        }
    }

    @Test
    void structuredObserverLeavesNoMdcBehind() {
        ListAppender<ILoggingEvent> app = capture(Level.TRACE);
        try {
            Slf4jCollaborationObserver obs = new Slf4jCollaborationObserver();
            obs.onEvent(new CoordinationEvent("t1", Type.FAIL, "w", "detail")); // WARN → always logs
            obs.onTaskCompleted("t1", Status.FAILED, 7);                        // WARN → always logs

            assertNull(MDC.get("taskId"), "MDC taskId cleared");
            assertNull(MDC.get("event"), "MDC event cleared");
            assertNull(MDC.get("workerId"), "MDC workerId cleared");
            assertNull(MDC.get("outcome"), "MDC outcome cleared");
            assertNull(MDC.get("durationMs"), "MDC durationMs cleared");
        } finally {
            detach(app);
        }
    }
}
