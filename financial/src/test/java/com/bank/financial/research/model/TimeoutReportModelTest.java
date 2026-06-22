package com.bank.financial.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.model.ReportModel.ModelTask;
import com.bank.financial.research.model.TimeoutReportModel.ModelTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit layer: the hard per-call timeout that protects the live LLM path. */
class TimeoutReportModelTest {

    private static final ModelTask TASK = new ModelTask("writer", "i", "b", 100);

    private static ReportModel of(String name, java.util.function.Function<ModelTask, String> fn) {
        return new ReportModel() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String generate(ModelTask t) {
                return fn.apply(t);
            }
        };
    }

    @Test
    void fastDelegate_passesThrough() {
        TimeoutReportModel m = new TimeoutReportModel(of("fast", t -> "ok"), Duration.ofSeconds(2));
        assertEquals("ok", m.generate(TASK));
    }

    @Test
    void slowDelegate_timesOut() {
        ReportModel slow = of("slow", t -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        });
        TimeoutReportModel m = new TimeoutReportModel(slow, Duration.ofMillis(50));
        assertThrows(ModelTimeoutException.class, () -> m.generate(TASK));
    }

    @Test
    void throwingDelegate_surfacesAsRuntimeException() {
        TimeoutReportModel m = new TimeoutReportModel(
                of("boom", t -> {
                    throw new IllegalStateException("upstream 500");
                }), Duration.ofSeconds(2));
        assertThrows(RuntimeException.class, () -> m.generate(TASK));
    }

    @Test
    void timeoutExceptionIsNonRetryable() {
        // Wiring guard: RetryReportModel keys off this marker to avoid re-running a
        // call whose time budget is already spent.
        assertTrue(new ModelTimeoutException("x") instanceof NonRetryable,
                "a hard timeout must be flagged non-retryable");
    }
}
