package com.huawei.ascend.examples.workmate.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PlanExtractService {

    private static final Pattern NUMBERED = Pattern.compile("^\\d+[.)]\\s+(.+)$");
    private static final Pattern BULLET = Pattern.compile("^[-*•]\\s+(.+)$");

    public Optional<PlanPayload> extract(String assistantText) {
        if (assistantText == null || assistantText.isBlank()) {
            return Optional.empty();
        }
        List<PlanPayload.PlanStep> steps = new ArrayList<>();
        for (String rawLine : assistantText.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String title = matchStepTitle(line);
            if (title == null || title.isBlank()) {
                continue;
            }
            steps.add(new PlanPayload.PlanStep("step-" + (steps.size() + 1), title.trim(), "pending"));
        }
        if (steps.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new PlanPayload(UUID.randomUUID().toString(), null, steps));
    }

    private static String matchStepTitle(String line) {
        Matcher numbered = NUMBERED.matcher(line);
        if (numbered.matches()) {
            return numbered.group(1);
        }
        Matcher bullet = BULLET.matcher(line);
        if (bullet.matches()) {
            return bullet.group(1);
        }
        return null;
    }
}
