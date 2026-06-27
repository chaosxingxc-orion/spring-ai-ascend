package com.huawei.ascend.examples.workmate.team;

/**
 * Message-bus member guidance (W26 workmate incubation).
 */
public final class TeamTopicBusContract {

    private TeamTopicBusContract() {}

    public static String memberPrompt(String publishToolId, String memberTopic) {
        return """
                Team message bus (async topic lanes):
                - Use %s to publish incremental findings for peers while you work.
                - Default topic for your lane: `%s` (omit topic param to use it).
                - Do not duplicate prior bus entries. If nothing new, reply with exactly: NO_NEW_FINDINGS.
                - Final assistant reply is still collected; prefer one consolidated publish or a short closing summary.
                """
                .formatted(publishToolId, memberTopic);
    }
}
