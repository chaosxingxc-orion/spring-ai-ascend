package com.huawei.ascend.examples.workmate.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AutomationScheduler.class);

    private final AutomationJobService automationJobService;

    public AutomationScheduler(AutomationJobService automationJobService) {
        this.automationJobService = automationJobService;
    }

    @Scheduled(fixedDelayString = "${workmate.automation.poll-interval-ms:60000}")
    public void pollDueJobs() {
        try {
            automationJobService.runDueJobs();
        } catch (Exception ex) {
            LOG.warn("Automation scheduler tick failed: {}", ex.getMessage());
        }
    }
}
