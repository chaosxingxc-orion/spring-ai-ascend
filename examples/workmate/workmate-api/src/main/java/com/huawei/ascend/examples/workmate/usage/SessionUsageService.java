package com.huawei.ascend.examples.workmate.usage;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionUsageService {

    private final SessionUsageRepository repository;

    public SessionUsageService(SessionUsageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SessionUsageTotals totalsForSession(UUID sessionId) {
        long promptTokens = 0L;
        long completionTokens = 0L;
        for (SessionUsageRecord record : repository.findBySessionId(sessionId)) {
            promptTokens += record.getPromptTokens();
            completionTokens += record.getCompletionTokens();
        }
        return new SessionUsageTotals(promptTokens, completionTokens);
    }

    @Transactional(readOnly = true)
    public SessionUsageTotals totalsForRun(UUID sessionId, String runId) {
        long promptTokens = 0L;
        long completionTokens = 0L;
        for (SessionUsageRecord record : repository.findBySessionIdAndRunId(sessionId, runId)) {
            promptTokens += record.getPromptTokens();
            completionTokens += record.getCompletionTokens();
        }
        return new SessionUsageTotals(promptTokens, completionTokens);
    }

    @Transactional
    public SessionUsageDelta recordModelUsage(UUID sessionId, String runId, TrajectoryEvent.Usage usage) {
        if (usage == null) {
            return null;
        }
        int promptTokens = usage.inputTokens() != null ? usage.inputTokens() : 0;
        int completionTokens = usage.outputTokens() != null ? usage.outputTokens() : 0;
        if (promptTokens == 0 && completionTokens == 0) {
            return null;
        }

        repository.save(new SessionUsageRecord(
                UUID.randomUUID(),
                sessionId,
                runId,
                promptTokens,
                completionTokens,
                usage.model()));

        SessionUsageTotals totals = totalsForSession(sessionId);
        return new SessionUsageDelta(
                promptTokens,
                completionTokens,
                totals.promptTokens(),
                totals.completionTokens(),
                usage.model());
    }

    public record SessionUsageDelta(
            int deltaPromptTokens,
            int deltaCompletionTokens,
            long totalPromptTokens,
            long totalCompletionTokens,
            String model) {}
}
