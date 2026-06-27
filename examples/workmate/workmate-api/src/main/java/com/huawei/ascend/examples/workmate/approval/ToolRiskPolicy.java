package com.huawei.ascend.examples.workmate.approval;

import com.huawei.ascend.examples.workmate.mcp.McpToolIdNaming;
import com.huawei.ascend.examples.workmate.security.SecurityPolicyService;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ToolRiskPolicy {

    private static final Pattern RM_PATTERN = Pattern.compile(
            "(^|[\\s;&|])rm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_BASH = Pattern.compile(
            "(^|[\\s;&|])(sudo|chmod\\s+777|mkfs|dd\\s+if=|curl\\s+[^|]*\\|\\s*(ba)?sh|wget\\s+[^|]*\\|\\s*(ba)?sh)",
            Pattern.CASE_INSENSITIVE);

    private ToolRiskPolicy() {
    }

    private static final Pattern MCP_SUBMIT_TOOL =
            Pattern.compile("^submit(_|$)", Pattern.CASE_INSENSITIVE);

    private static volatile SecurityPolicyService policyService;

    public static void bind(SecurityPolicyService service) {
        policyService = service;
    }

    public static RiskAssessment assess(String toolName, Map<String, Object> args) {
        RiskAssessment base = assessInternal(toolName, args);
        SecurityPolicyService policies = policyService;
        if (policies != null) {
            return policies.overlay(base, toolName, args);
        }
        return base;
    }

    private static RiskAssessment assessInternal(String toolName, Map<String, Object> args) {
        if (WorkmateToolIds.isBash(toolName)) {
            return assessBash(asString(args.get("command")));
        }
        if (WorkmateToolIds.isWrite(toolName) || WorkmateToolIds.isRead(toolName)) {
            return RiskAssessment.none();
        }
        if (toolName != null && toolName.startsWith("mcp__")) {
            return assessMcp(toolName, args);
        }
        return RiskAssessment.none();
    }

    private static RiskAssessment assessMcp(String toolName, Map<String, Object> args) {
        try {
            McpToolIdNaming.McpToolRef ref = McpToolIdNaming.parseOpenJiuwenToolId(toolName);
            if (!MCP_SUBMIT_TOOL.matcher(ref.toolName()).find()) {
                return RiskAssessment.none();
            }
            String summary = mcpSubmissionSummary(args);
            return new RiskAssessment(
                    "HIGH",
                    "Business submission via MCP (" + ref.serverId() + ")",
                    summary);
        } catch (IllegalArgumentException ignored) {
            return RiskAssessment.none();
        }
    }

    private static String mcpSubmissionSummary(Map<String, Object> args) {
        List<String> parts = new ArrayList<>();
        appendArg(parts, "操作", args, "operation", "action", "type");
        appendArg(parts, "客户", args, "customerName", "customer", "client");
        appendArg(parts, "企业", args, "companyName", "company");
        appendArg(parts, "额度", args, "creditAmount", "amount", "limit");
        if (parts.isEmpty()) {
            return "MCP business submission";
        }
        return String.join(" | ", parts);
    }

    private static void appendArg(
            List<String> parts, String label, Map<String, Object> args, String... keys) {
        for (String key : keys) {
            String value = asString(args.get(key));
            if (!value.isBlank()) {
                parts.add(label + ": " + value);
                return;
            }
        }
    }

    private static RiskAssessment assessBash(String command) {
        if (command.isBlank()) {
            return RiskAssessment.none();
        }
        if (RM_PATTERN.matcher(command).find()) {
            return new RiskAssessment("HIGH", "Delete files (rm)", command);
        }
        if (DANGEROUS_BASH.matcher(command).find()) {
            return new RiskAssessment("CRITICAL", "Potentially destructive shell command", command);
        }
        return RiskAssessment.none();
    }

    public record RiskAssessment(String level, String reason, String summary) {
        public boolean requiresApproval() {
            return level != null && !policyBlocked();
        }

        public boolean policyBlocked() {
            return "CRITICAL".equals(level) && "Blocked by security policy".equals(reason);
        }

        static RiskAssessment none() {
            return new RiskAssessment(null, null, null);
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
