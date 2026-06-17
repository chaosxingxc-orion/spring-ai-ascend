package com.bank.financial.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.model.ReportModel.ModelTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit layer: bounded retry-with-backoff for transient model failures. */
class RetryReportModelTest {

    private static final ModelTask TASK = new ModelTask("writer", "i", "b", 100);

    private static ReportModel failingThenOk(AtomicInteger calls, int failTimes) {
        return new ReportModel() {
            @Override
            public String name() {
                return "flaky";
            }

            @Override
            public String generate(ModelTask t) {
                if (calls.getAndIncrement() < failTimes) {
                    throw new RuntimeException("java.net.ConnectException");
                }
                return "ok";
            }
        };
    }

    @Test
    void retriesTransientThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        RetryReportModel m = new RetryReportModel(failingThenOk(calls, 2), 3, 1); // 1ms base → fast
        assertEquals("ok", m.generate(TASK));
        assertEquals(3, calls.get()); // failed twice, succeeded on third
    }

    @Test
    void rethrowsAfterExhaustingAttempts() {
        AtomicInteger calls = new AtomicInteger();
        RetryReportModel m = new RetryReportModel(failingThenOk(calls, 99), 3, 1);
        assertThrows(RuntimeException.class, () -> m.generate(TASK));
        assertEquals(3, calls.get()); // exactly maxAttempts
    }

    @Test
    void backoffIsBoundedByCap() {
        RetryReportModel m = new RetryReportModel(failingThenOk(new AtomicInteger(), 0), 5, 1000, 4000);
        for (int attempt = 1; attempt <= 6; attempt++) {
            assertTrue(m.backoffMs(attempt) <= 4000, "backoff must respect cap");
        }
    }
}
