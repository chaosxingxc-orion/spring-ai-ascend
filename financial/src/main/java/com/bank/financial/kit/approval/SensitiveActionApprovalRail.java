package com.bank.financial.kit.approval;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import java.util.Locale;

/**
 * Human-in-the-loop approval for sensitive/irreversible actions (transfers,
 * limit changes, account modifications, fee waivers, …). Subclass it, name the
 * tools that are sensitive, and decide per call whether approval is required.
 *
 * <p>Flow (provided by the platform's interrupt machinery):
 * <ol>
 *   <li>First pass, no decision yet → if {@link #requiresApproval} is true the
 *       run PAUSES (returns {@code result_type:"interrupt"}); push the request
 *       to your approval queue.</li>
 *   <li>Resume the same conversation with the approver's decision as the input;
 *       {@link #isApproved} interprets it — approve runs the tool for real,
 *       reject returns a refusal and the money/action never happens.</li>
 * </ol>
 * Pair with a durable checkpointer (Redis) so the pause survives process
 * restarts and any worker can resume — see the guide.
 */
public abstract class SensitiveActionApprovalRail extends BaseInterruptRail {

    protected SensitiveActionApprovalRail(Iterable<String> sensitiveToolNames) {
        super(sensitiveToolNames);
    }

    @Override
    protected InterruptDecision resolveInterrupt(
            AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        if (userInput != null) {
            // Resume leg: the approver has decided.
            return isApproved(userInput) ? approve() : reject(rejectionResult(toolCall, userInput));
        }
        // First leg: pause if this specific call needs human sign-off.
        return requiresApproval(ctx, toolCall)
                ? interrupt(buildApprovalRequest(ctx, toolCall))
                : approve();
    }

    /** True if THIS call must be paused for human approval (e.g. amount over a limit). */
    protected abstract boolean requiresApproval(AgentCallbackContext ctx, ToolCall toolCall);

    /** Build the approval ask surfaced to the approver (message + context payload). */
    protected abstract InterruptRequest buildApprovalRequest(AgentCallbackContext ctx, ToolCall toolCall);

    /** Interpret the approver's decision. Override for a richer protocol. */
    protected boolean isApproved(Object userInput) {
        String s = String.valueOf(userInput).trim().toLowerCase(Locale.ROOT);
        return s.startsWith("approve") || s.equals("yes") || s.equals("y")
                || s.equals("true") || s.equals("同意") || s.equals("批准") || s.equals("通过");
    }

    /** Synthetic tool result fed back to the model when the action is rejected. */
    protected Object rejectionResult(ToolCall toolCall, Object userInput) {
        return "操作未通过审批,已拒绝执行。";
    }
}
