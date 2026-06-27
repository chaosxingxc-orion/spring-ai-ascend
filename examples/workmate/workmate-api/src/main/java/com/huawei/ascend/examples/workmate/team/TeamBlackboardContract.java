package com.huawei.ascend.examples.workmate.team;

/**
 * Shared team blackboard layout (W27 interim + W27 shared-state versioning).
 */
public final class TeamBlackboardContract {

    public static final String ROOT = "team";
    public static final String BLACKBOARD_FILE = "blackboard.md";
    public static final String META_FILE = "blackboard.meta.json";

    private TeamBlackboardContract() {}

    public static String relativePath(String parentRunId) {
        return ROOT + "/" + sanitize(parentRunId) + "/" + BLACKBOARD_FILE;
    }

    public static String metaRelativePath(String parentRunId) {
        return ROOT + "/" + sanitize(parentRunId) + "/" + META_FILE;
    }

    public static String blackboardPrompt(String relativePath) {
        return """
                Team shared blackboard (shared-state):
                - Path: %s
                - Read this file for prior member contributions before you act.
                - Do not duplicate work already recorded on the blackboard.
                - Your output will be appended to the blackboard for other members (you do not edit the file directly).
                """.formatted(relativePath);
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown-run";
        }
        return raw.replaceAll("[^a-zA-Z0-9._:-]+", "-");
    }
}
