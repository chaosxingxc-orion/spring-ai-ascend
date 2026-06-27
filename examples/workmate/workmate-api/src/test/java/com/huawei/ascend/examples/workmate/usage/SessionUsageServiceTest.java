package com.huawei.ascend.examples.workmate.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class SessionUsageServiceTest {

    @Autowired
    private SessionUsageService sessionUsageService;

    @Autowired
    private SessionUsageRepository repository;

    @BeforeEach
    void resetUsage() {
        // Methods share one in-memory datasource and the suite runs without per-method rollback;
        // clear rows so global assertions (e.g. repository.findAll()) only see this test's writes.
        repository.deleteAll();
    }

    @DynamicPropertySource
    static void registerH2(DynamicPropertyRegistry registry) {
        WorkmateTestProperties.registerH2(registry, "usage");
    }

    @Test
    void recordModelUsageAccumulatesTotals() {
        UUID sessionId = UUID.randomUUID();
        TrajectoryEvent.Usage usage = new TrajectoryEvent.Usage(100, 50, 12.0, "deepseek-chat");

        SessionUsageService.SessionUsageDelta first =
                sessionUsageService.recordModelUsage(sessionId, "run-1", usage);
        SessionUsageService.SessionUsageDelta second =
                sessionUsageService.recordModelUsage(
                        sessionId,
                        "run-1",
                        new TrajectoryEvent.Usage(20, 10, 5.0, "deepseek-chat"));

        assertThat(first).isNotNull();
        assertThat(first.totalPromptTokens()).isEqualTo(100);
        assertThat(first.totalCompletionTokens()).isEqualTo(50);

        assertThat(second).isNotNull();
        assertThat(second.totalPromptTokens()).isEqualTo(120);
        assertThat(second.totalCompletionTokens()).isEqualTo(60);

        SessionUsageTotals totals = sessionUsageService.totalsForSession(sessionId);
        assertThat(totals.promptTokens()).isEqualTo(120);
        assertThat(totals.completionTokens()).isEqualTo(60);
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void totalsForRunScopesToRunId() {
        UUID sessionId = UUID.randomUUID();
        sessionUsageService.recordModelUsage(
                sessionId, "parent:writer", new TrajectoryEvent.Usage(40, 20, 1.0, "m"));
        sessionUsageService.recordModelUsage(
                sessionId, "parent:writer", new TrajectoryEvent.Usage(10, 5, 1.0, "m"));
        sessionUsageService.recordModelUsage(
                sessionId, "parent:other", new TrajectoryEvent.Usage(99, 99, 1.0, "m"));

        SessionUsageTotals writer = sessionUsageService.totalsForRun(sessionId, "parent:writer");
        assertThat(writer.promptTokens()).isEqualTo(50);
        assertThat(writer.completionTokens()).isEqualTo(25);
    }

    @Test
    void recordModelUsageSkipsEmptyUsage() {
        UUID sessionId = UUID.randomUUID();
        assertThat(sessionUsageService.recordModelUsage(sessionId, "run-1", null)).isNull();
        assertThat(sessionUsageService.recordModelUsage(
                sessionId, "run-1", new TrajectoryEvent.Usage(0, 0, null, null)))
                .isNull();
    }
}
