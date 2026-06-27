package com.huawei.ascend.examples.workmate.automation;

import com.huawei.ascend.examples.workmate.agent.AgentRunService;
import com.huawei.ascend.examples.workmate.automation.dto.AutomationJobResponse;
import com.huawei.ascend.examples.workmate.automation.dto.CreateAutomationJobRequest;
import com.huawei.ascend.examples.workmate.automation.dto.UpdateAutomationJobRequest;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionBusyException;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionRequest;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationJobService {

    private static final Logger LOG = LoggerFactory.getLogger(AutomationJobService.class);
    private static final DateTimeFormatter TITLE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());

    private final AutomationJobRepository repository;
    private final SessionService sessionService;
    private final AgentRunService agentRunService;
    private final WorkmateLlmProperties llm;

    public AutomationJobService(
            AutomationJobRepository repository,
            SessionService sessionService,
            AgentRunService agentRunService,
            WorkmateLlmProperties llm) {
        this.repository = repository;
        this.sessionService = sessionService;
        this.agentRunService = agentRunService;
        this.llm = llm;
    }

    @Transactional(readOnly = true)
    public List<AutomationJobResponse> listJobs() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AutomationJobResponse getJob(UUID id) {
        return toResponse(requireJob(id));
    }

    @Transactional
    public AutomationJobResponse createJob(CreateAutomationJobRequest request) {
        String cron = normalizeCronOrDefault(request.cronExpression());
        AutomationCronSupport.validateCron(cron);

        AutomationJob job = new AutomationJob();
        job.setId(UUID.randomUUID());
        job.setName(request.name().trim());
        job.setExpertId(blankToNull(request.expertId()));
        job.setPromptText(request.promptText().trim());
        job.setCronExpression(cron);
        job.setEnabled(request.enabled() == null || request.enabled());
        job.setNextRunAt(job.isEnabled() ? AutomationCronSupport.computeNextRun(cron, Instant.now()) : null);

        return toResponse(repository.save(job));
    }

    @Transactional
    public AutomationJobResponse updateJob(UUID id, UpdateAutomationJobRequest request) {
        AutomationJob job = requireJob(id);
        if (request.name() != null) {
            job.setName(request.name().trim());
        }
        if (request.expertId() != null) {
            job.setExpertId(blankToNull(request.expertId()));
        }
        if (request.promptText() != null) {
            job.setPromptText(request.promptText().trim());
        }
        if (request.cronExpression() != null) {
            String cron = normalizeCronOrDefault(request.cronExpression());
            AutomationCronSupport.validateCron(cron);
            job.setCronExpression(cron);
        }
        if (request.enabled() != null) {
            job.setEnabled(request.enabled());
        }
        refreshNextRun(job);
        return toResponse(repository.save(job));
    }

    @Transactional
    public void deleteJob(UUID id) {
        repository.delete(requireJob(id));
    }

    @Transactional
    public AutomationJobResponse runNow(UUID id) {
        AutomationJob job = requireJob(id);
        executeJob(job);
        return toResponse(repository.save(job));
    }

    @Transactional
    public void runDueJobs() {
        Instant now = Instant.now();
        for (AutomationJob job : repository.findDueJobs(now)) {
            try {
                executeJob(job);
                repository.save(job);
            } catch (Exception ex) {
                LOG.warn("Automation job {} failed in scheduler: {}", job.getId(), ex.getMessage());
            }
        }
    }

    private void executeJob(AutomationJob job) {
        Instant started = Instant.now();
        job.setLastRunAt(started);
        job.setLastError(null);

        if (!llm.isConfigured()) {
            job.setLastStatus(AutomationRunStatus.SKIPPED);
            job.setLastError("LLM not configured");
            refreshNextRun(job);
            return;
        }

        try {
            String title = job.getName() + " · " + TITLE_TIME.format(started);
            CreateSessionRequest createRequest = new CreateSessionRequest(
                    title,
                    null,
                    job.getExpertId(),
                    PermissionMode.CRAFT,
                    null,
                    null,
                    true,
                    null,
                    null,
                    null);
            CreateSessionResponse created = sessionService.createSession(createRequest);
            UUID sessionId = created.session().id();
            agentRunService.runPromptFireAndForget(sessionId, job.getPromptText());
            job.setLastSessionId(sessionId);
            job.setLastStatus(AutomationRunStatus.SUCCESS);
        } catch (SessionBusyException ex) {
            job.setLastStatus(AutomationRunStatus.SKIPPED);
            job.setLastError("Session busy");
        } catch (Exception ex) {
            job.setLastStatus(AutomationRunStatus.FAILED);
            job.setLastError(truncate(ex.getMessage(), 2000));
            LOG.warn("Automation job {} execution failed", job.getId(), ex);
        }
        refreshNextRun(job);
    }

    private void refreshNextRun(AutomationJob job) {
        if (!job.isEnabled()) {
            job.setNextRunAt(null);
            return;
        }
        job.setNextRunAt(AutomationCronSupport.computeNextRun(job.getCronExpression(), Instant.now()));
    }

    private AutomationJob requireJob(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Automation job not found: " + id));
    }

    private AutomationJobResponse toResponse(AutomationJob job) {
        return new AutomationJobResponse(
                job.getId(),
                job.getName(),
                job.isEnabled(),
                job.getExpertId(),
                job.getPromptText(),
                job.getCronExpression(),
                formatInstant(job.getNextRunAt()),
                formatInstant(job.getLastRunAt()),
                job.getLastSessionId(),
                job.getLastStatus() != null ? job.getLastStatus().name() : null,
                job.getLastError(),
                formatInstant(job.getCreatedAt()),
                formatInstant(job.getUpdatedAt()));
    }

    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private static String normalizeCronOrDefault(String cron) {
        if (cron == null || cron.isBlank()) {
            return "0 9 * * *";
        }
        return AutomationCronSupport.normalizeCron(cron);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
