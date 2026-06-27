package com.huawei.ascend.examples.workmate.office;

import java.util.Map;

public record TeamMemberDefinition(
        String id,
        String name,
        String expertId,
        String role,
        int order,
        String avatar,
        String participantRole,
        Map<String, String> profession,
        String nickname,
        TeamMemberBackend backend,
        TeamMemberRuntimeConfig runtime) {

    public TeamMemberDefinition(
            String id,
            String name,
            String expertId,
            String role,
            int order,
            String avatar) {
        this(id, name, expertId, role, order, avatar, null, null, null, TeamMemberBackend.LOCAL, null);
    }

    /** Backward-compatible constructor (pre A3: without profession/nickname). */
    public TeamMemberDefinition(
            String id,
            String name,
            String expertId,
            String role,
            int order,
            String avatar,
            String participantRole) {
        this(id, name, expertId, role, order, avatar, participantRole, null, null, TeamMemberBackend.LOCAL, null);
    }

    /** Backward-compatible constructor (pre W51: without backend/runtime). */
    public TeamMemberDefinition(
            String id,
            String name,
            String expertId,
            String role,
            int order,
            String avatar,
            String participantRole,
            Map<String, String> profession,
            String nickname) {
        this(id, name, expertId, role, order, avatar, participantRole, profession, nickname, TeamMemberBackend.LOCAL, null);
    }

    public TeamMemberDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("team member id is required");
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (expertId == null || expertId.isBlank()) {
            throw new IllegalArgumentException("team member expertId is required for " + id);
        }
        if (profession == null) {
            profession = Map.of();
        }
        if (backend == null) {
            backend = TeamMemberBackend.LOCAL;
        }
    }

    public String resolvedProfession(String lang) {
        if (profession.containsKey(lang)) {
            return profession.get(lang);
        }
        if (profession.containsKey("zh")) {
            return profession.get("zh");
        }
        if (!profession.isEmpty()) {
            return profession.values().iterator().next();
        }
        return "";
    }
}
