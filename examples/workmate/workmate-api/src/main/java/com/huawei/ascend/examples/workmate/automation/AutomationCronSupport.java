package com.huawei.ascend.examples.workmate.automation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

final class AutomationCronSupport {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private AutomationCronSupport() {}

    static Instant computeNextRun(String cronExpression, Instant after) {
        CronExpression parsed = CronExpression.parse(normalizeCron(cronExpression));
        ZonedDateTime next = parsed.next(after.atZone(ZONE));
        if (next == null) {
            throw new IllegalArgumentException("Cron expression has no upcoming execution: " + cronExpression);
        }
        return next.toInstant();
    }

    static void validateCron(String cronExpression) {
        CronExpression.parse(normalizeCron(cronExpression));
    }

    static String normalizeCron(String raw) {
        String trimmed = raw.trim();
        int fields = trimmed.split("\\s+").length;
        if (fields == 5) {
            return "0 " + trimmed;
        }
        if (fields != 6) {
            throw new IllegalArgumentException("Cron must have 5 or 6 fields: " + raw);
        }
        return trimmed;
    }
}
