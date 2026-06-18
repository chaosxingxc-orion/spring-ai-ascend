package com.bank.financial.kit.approval;

import com.bank.financial.kit.spec.AgentDefinition.ApprovalRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link SensitiveActionApprovalRail} driven entirely by declarative
 * {@link ApprovalRule}s from YAML — so human-approval gates need no Java.
 *
 * <p>For each sensitive tool: if the rule has no {@code amountOver}, every call
 * pauses; if it does, only calls whose {@code amountField} argument exceeds the
 * threshold pause. The rest run straight through.
 */
public final class RuleBasedApprovalRail extends SensitiveActionApprovalRail {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ApprovalRule> rules;

    public RuleBasedApprovalRail(List<ApprovalRule> rules) {
        super(toolNamesOf(rules));
        this.rules = rules;
    }

    private static List<String> toolNamesOf(List<ApprovalRule> rules) {
        List<String> names = new ArrayList<>();
        for (ApprovalRule r : rules) {
            if (r.tools() != null) {
                for (String t : r.tools()) {
                    if (!names.contains(t)) {
                        names.add(t);
                    }
                }
            }
        }
        return names;
    }

    @Override
    protected boolean requiresApproval(AgentCallbackContext ctx, ToolCall toolCall) {
        ApprovalRule rule = ruleFor(toolCall);
        if (rule == null) {
            return false;
        }
        if (rule.amountOver() == null) {
            return true;
        }
        Double amount = numericArg(toolCall, rule.amountField());
        return amount != null && amount > rule.amountOver();
    }

    @Override
    protected InterruptRequest buildApprovalRequest(AgentCallbackContext ctx, ToolCall toolCall) {
        ApprovalRule rule = ruleFor(toolCall);
        return InterruptRequest.builder()
                .interruptId(safeId(toolCall))
                .message(rule != null ? rule.message() : "该操作需要人工审批确认。")
                .context(args(toolCall))
                .build();
    }

    private ApprovalRule ruleFor(ToolCall toolCall) {
        String name = safeName(toolCall);
        for (ApprovalRule r : rules) {
            if (r.tools() != null && r.tools().contains(name)) {
                return r;
            }
        }
        return null;
    }

    private Double numericArg(ToolCall toolCall, String field) {
        Object v = args(toolCall).get(field);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v).replaceAll("[,_\\s]", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> args(ToolCall toolCall) {
        try {
            String json = toolCall.getArguments();
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>();
            }
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String safeName(ToolCall toolCall) {
        try {
            return toolCall.getName();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeId(ToolCall toolCall) {
        try {
            String id = toolCall.getId();
            return id == null ? "approval" : id;
        } catch (Exception e) {
            return "approval";
        }
    }
}
