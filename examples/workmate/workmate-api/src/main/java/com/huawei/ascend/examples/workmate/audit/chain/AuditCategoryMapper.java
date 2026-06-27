package com.huawei.ascend.examples.workmate.audit.chain;

import java.util.Locale;
import java.util.Map;

public final class AuditCategoryMapper {

    public record Classification(String category, String decision) {
    }

    private AuditCategoryMapper() {
    }

    public static Classification classify(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return new Classification("system", "info");
        }
        String name = eventName.toLowerCase(Locale.ROOT);
        if (name.startsWith("approval.")) {
            return classifyApproval(name);
        }
        if (name.startsWith("tool.")) {
            return new Classification("sandbox", "info");
        }
        if (name.startsWith("team.")) {
            return new Classification("system", "info");
        }
        if (name.contains("file") || name.equals("file.reverted")) {
            return new Classification("fileSafety", "info");
        }
        if (name.startsWith("mcp.")) {
            return classifyMcp(name);
        }
        if (name.contains("bash") || name.contains("command")) {
            return new Classification("commandSafety", "info");
        }
        if (name.contains("network")) {
            return new Classification("network", "info");
        }
        if (name.equals("run.failed") || name.equals("run.error")) {
            return new Classification("system", "failed");
        }
        if (name.equals("conversation.truncated")) {
            return new Classification("security", "info");
        }
        if (name.startsWith("studio.")) {
            return new Classification("security", "info");
        }
        return new Classification("system", "info");
    }

    private static Classification classifyApproval(String eventName) {
        if (eventName.contains("decided")) {
            return new Classification("approval", decisionFromPayload(eventName));
        }
        if (eventName.contains("required")) {
            return new Classification("approval", "blocked");
        }
        return new Classification("approval", "info");
    }

    private static Classification classifyMcp(String eventName) {
        if (eventName.contains("approve")) {
            return new Classification("mcp", "approved");
        }
        if (eventName.contains("reject") || eventName.contains("deny")) {
            return new Classification("mcp", "rejected");
        }
        return new Classification("mcp", "info");
    }

    private static String decisionFromPayload(String eventName) {
        return eventName.contains("deny") ? "rejected" : "approved";
    }

    public static Classification classifyWithPayload(String eventName, Map<String, Object> payload) {
        Classification base = classify(eventName);
        if (!"approval".equals(base.category()) || payload == null) {
            return base;
        }
        Object decision = payload.get("decision");
        if (decision instanceof String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("deny") || lower.contains("reject")) {
                return new Classification("approval", "rejected");
            }
            if (lower.contains("approve")) {
                return new Classification("approval", "approved");
            }
        }
        return base;
    }
}
