package com.bank.financial.playground;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import java.util.List;

/**
 * Prints a human-readable trace of one agent turn to stdout — so a non-expert
 * can SEE what happened (model thoughts, tool calls + results, the final
 * answer) without standing up OTel/Jaeger. Attached by the playground; it is a
 * normal {@link AgentRail}, so the same trace can be turned on anywhere.
 */
public final class TraceRail extends AgentRail {

    private static final int MAX = 600;

    public TraceRail() {
        setPriority(1); // observe early
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof InvokeInputs in) {
            line("👤 用户", in.getQuery());
        }
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        System.out.println("  🧠 调用模型…");
    }

    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof ModelCallInputs in
                && in.getResponse() instanceof AssistantMessage out) {
            Object content = out.getContent();
            if (content != null && !String.valueOf(content).isBlank()) {
                line("  💬 模型输出", String.valueOf(content));
            }
            List<ToolCall> calls = out.getToolCalls();
            if (calls != null) {
                for (ToolCall tc : calls) {
                    System.out.println("  🔧 请求工具: " + tc.getName() + "  " + trunc(tc.getArguments()));
                }
            }
        }
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof ToolCallInputs in) {
            line("  ⚙️  执行工具 " + in.getToolName(), String.valueOf(in.getToolArgs()));
        }
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        // On an approval interrupt the tool never runs, so result is null — skip it
        // (the ⏸ pause line tells the real story).
        if (ctx.getInputs() instanceof ToolCallInputs in && in.getToolResult() != null) {
            line("  📦 工具返回", String.valueOf(in.getToolResult()));
        }
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof InvokeInputs in && in.getResult() != null) {
            Object type = in.getResult().get("result_type");
            Object output = in.getResult().get("output");
            if ("interrupt".equals(type)) {
                System.out.println("  ⏸  暂停:等待人工审批(result_type=interrupt)");
            } else {
                line("✅ 最终[" + type + "]", String.valueOf(output));
            }
        }
    }

    private static void line(String label, String value) {
        System.out.println(label + ": " + trunc(value));
    }

    private static String trunc(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > MAX ? one.substring(0, MAX) + "…" : one;
    }
}
