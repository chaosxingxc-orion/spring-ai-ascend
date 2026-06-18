package com.huawei.ascend.a2a.memory.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.OwnershipViolationException;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Dual-mode observation: composite fan-out + fault isolation, level routing, MDC leak-safety, kit wiring. */
class MemoryObserverTest {

    private static final class Recording implements MemoryObserver {
        final List<String> ops = new ArrayList<>();
        final List<String> degraded = new ArrayList<>();

        @Override
        public void onOperation(String op, String scope, boolean ok, long latencyMs) {
            ops.add(op + ":" + ok);
        }

        @Override
        public void onDegraded(String op, String scope, String reason) {
            degraded.add(op + ":" + reason);
        }
    }

    @Test
    void compositeFansOutAndIsolatesFailures() {
        MemoryObserver boom = new MemoryObserver() {
            @Override public void onOperation(String op, String s, boolean ok, long ms) {
                throw new RuntimeException("boom");
            }
            @Override public void onDegraded(String op, String s, String r) {
                throw new RuntimeException("boom");
            }
        };
        Recording good = new Recording();
        CompositeMemoryObserver obs = CompositeMemoryObserver.of(boom, good);
        obs.onOperation("recall", "t", true, 1);
        obs.onDegraded("recall", "t", "circuit-open");
        assertEquals(List.of("recall:true"), good.ops, "healthy delegate still observed despite a failing one");
        assertEquals(List.of("recall:circuit-open"), good.degraded);
    }


    @Test
    void sharedKitReportsOwnershipRejectionAndStillThrows() {
        Recording rec = new Recording();
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(
                new InMemorySharedMemoryStore(() -> 0L), "bank", "c1", rec);
        board.put("k", "v", "owner");
        assertThrows(OwnershipViolationException.class, () -> board.put("k", "x", "intruder"));
        assertTrue(rec.degraded.contains("shared.put:ownership-rejected"), "ownership reject is observed");
        assertTrue(rec.ops.contains("shared.put:true"), "successful put observed");
    }

    @Test
    void slf4jObserverRoutesLevelsAndLeavesNoMdc() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("a2amem");
        log.setLevel(Level.TRACE);
        log.setAdditive(false);
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        log.addAppender(app);
        try {
            Slf4jMemoryObserver obs = new Slf4jMemoryObserver();   // runtime-lean
            obs.onOperation("recall", "t", true, 3);               // routine → DEBUG
            obs.onDegraded("recall", "t", "circuit-open");         // problem → WARN
            assertEquals(Level.DEBUG, levelOf(app, "op=recall"));
            assertEquals(Level.WARN, levelOf(app, "degraded"));
            assertNull(MDC.get("op"), "MDC cleared");
            assertNull(MDC.get("scope"));
        } finally {
            log.detachAppender(app);
        }
    }

    @Test
    void verboseObserverPromotesRoutineToInfo() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("a2amem");
        log.setLevel(Level.INFO);
        log.setAdditive(false);
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        log.addAppender(app);
        try {
            Slf4jMemoryObserver.verbose().onOperation("recall", "t", true, 3);
            assertEquals(Level.INFO, levelOf(app, "op=recall"), "verbose dev mode → routine at INFO");
        } finally {
            log.detachAppender(app);
        }
    }

    private static Level levelOf(ListAppender<ILoggingEvent> app, String contains) {
        return app.list.stream()
                .filter(e -> e.getFormattedMessage().contains(contains))
                .map(ILoggingEvent::getLevel)
                .findFirst().orElse(null);
    }
}
