package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.session.PermissionMode;

/**
 * reference-aligned HITL rules: only the team lead may ask the end user; members hand back to the lead.
 */
public final class TeamUserInteractionContract {

    public static final String LEADER_AGENT_ID = "leader";

    private TeamUserInteractionContract() {}

    public static boolean isLeaderAgent(String agentId) {
        return LEADER_AGENT_ID.equals(agentId);
    }

    public static String memberHitlRules(String askToolId) {
        return """
                Team member user-interaction rules (reference-aligned):
                - NEVER call %s or ask the end user directly.
                - If parameters are missing or ambiguous, finish with a structured handback to the team lead.
                - Prefix gaps with 【需主编决策】 and state exactly what the lead must clarify or supply.
                - The team lead confirms with the user and may re-delegate with an updated task packet.
                """.formatted(askToolId);
    }

    public static String memberSendMessageRules() {
        return """
                Team member handback rules (the SendMessage protocol — runtime-injected, mandatory):
                - When your task is complete, you MUST call send_message with to="team-lead" and the FULL deliverable in content.
                - Put the complete output described in your expert prompt's output contract into content; include summary= for a short card title.
                - Do NOT rely on assistant text alone — the lead only accepts structured handbacks via send_message.
                - Accepted to values: team-lead, lead, leader, main.
                """;
    }

    /** Injected for team leads (openjiuwen TeamAgent / predefined roster). */
    public static String leaderSendMessageRules() {
        return """
                Team lead messaging rules (reference-aligned — runtime-injected):
                - Delegate with send_message: to= member Agent ID (never display name); content= full task packet / research parameter card.
                - Predefined roster members are already registered — use send_message only, not spawn_member.
                - Collect member deliverables from send_message handbacks in your inbox; do not treat member assistant text as authoritative.
                - While members are still running, wait for their send_message handbacks; do not declare timeout degradation until the runtime signals members idle or the team time budget expires.
                - When relaying to the next member, forward the full prior deliverable inside your delegation content.
                """;
    }

    /**
     * Injected for team leads — prose alone cannot reach the user; HITL must go through the ask tool.
     */
    public static String leaderHitlRules(String askToolId) {
        return """
                Team lead user-confirmation rules (runtime-injected, mandatory):
                - ONLY you may call %s; members must hand back to you instead.
                - Any gate that needs user approval (research parameters, chapter outline, scope change) MUST call %s in the same turn.
                - NEVER write「提交用户确认」「等待用户确认」「请用户确认」as narration only — that does NOT render a confirmation card.
                - After calling %s you MUST wait for the tool result before build_team, send_message, or the next phase.
                - Progress templates may report status, but「下一步：提交用户确认」is invalid unless %s was just invoked.
                """.formatted(askToolId, askToolId, askToolId, askToolId);
    }

    /** Appended to leader input when research-planner returns a chapter outline (full-mode HITL gate). */
    public static String outlineConfirmationReminder(String askToolId) {
        return """

                【运行时强制 · 大纲确认】你已收到季要纲（research-planner）的章节大纲。
                在完整模式下，本回合你必须调用 %s，向用户展示大纲并等待确认。
                禁止仅用旁白写「提交用户确认」——那不会触发 UI 确认卡。
                建议 options：「确认大纲，继续逐章研究」「需要调整大纲」；allowFreeText=true。
                收到工具返回后，才可 send_message 派活或进入 Phase 3。
                """.formatted(askToolId);
    }

    public static boolean isResearchPlannerOutlineHandback(String fromMemberId, String body) {
        if (!"research-planner".equals(fromMemberId) || body == null || body.isBlank()) {
            return false;
        }
        return body.contains("\"sections\"")
                || body.contains("章节大纲")
                || (body.contains("\"title\"") && body.contains("sections"));
    }

    public static String memberWorkspaceRulesPrompt(
            String readId, String writeId, String bashId, PermissionMode mode) {
        if (mode == PermissionMode.ASK) {
            return """
                    You are a team member in Ask mode: use %s and MCP read tools only.
                    Do NOT call write or bash tools. Do NOT ask the end user; hand back to the team lead.
                    Be concise.
                    """.formatted(readId);
        }
        if (mode == PermissionMode.PLAN) {
            return """
                    You are a team member in Plan mode: analyze with %s and MCP tools, then reply with your plan.
                    Do NOT call write or bash tools yet. Do NOT ask the end user; hand back to the team lead.
                    """.formatted(readId);
        }
        return """
                You are a team member working inside one task session workspace.
                Use %s, %s, and %s ONLY inside the session workspace.
                Prefer relative paths. When asked to create a file, call %s.
                Do NOT ask the end user; report gaps to the team lead instead.
                Be concise; confirm what you created or changed.
                """.formatted(readId, writeId, bashId, writeId);
    }
}
