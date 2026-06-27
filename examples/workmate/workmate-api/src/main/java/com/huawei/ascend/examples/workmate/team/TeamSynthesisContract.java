package com.huawei.ascend.examples.workmate.team;

/** Shared lead-synthesis instructions for orchestrator / agent-team runs. */
public final class TeamSynthesisContract {

    private TeamSynthesisContract() {}

    public static String synthesisInstructions() {
        return """
                Synthesize a unified, actionable response for the user in markdown.
                Rules:
                - Output the final answer once. Do not repeat the same report, TL;DR, or section headings.
                - Use valid markdown: put a blank line before headings; use "# Title" with a space after #.
                - Summarize member findings; do not paste the full blackboard verbatim.
                - Only mention a workspace file path if you actually wrote that file in this run.
                - Prefer pointing users to team blackboard artifacts when no separate deliverable exists.
                """;
    }
}
