package com.huawei.ascend.examples.workmate.session;

import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertService;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactContract;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Builds soft-handoff context when switching experts mid-session (W53). */
@Service
public class ExpertHandoffService {

    private static final int MAX_DIALOGUE_LINES = 12;
    private static final int MAX_ARTIFACT_LINES = 16;
    private static final int LINE_PREVIEW_CHARS = 160;

    private final SessionPersistenceService sessionPersistenceService;
    private final ArtifactService artifactService;
    private final ExpertService expertService;

    public ExpertHandoffService(
            SessionPersistenceService sessionPersistenceService,
            @Lazy ArtifactService artifactService,
            ExpertService expertService) {
        this.sessionPersistenceService = sessionPersistenceService;
        this.artifactService = artifactService;
        this.expertService = expertService;
    }

    public String buildHandoffPrompt(
            UUID sessionId, String fromExpertId, String toExpertId, Path workspaceRoot) {
        String fromLabel = expertLabel(fromExpertId);
        String toLabel = expertLabel(toExpertId);
        String dialogue = summarizeRecentDialogue(sessionId);
        String artifacts = indexWorkspaceArtifacts(workspaceRoot, sessionId);
        return """
                Prior expert handoff (continue this session — do not restart from scratch):
                - Previous expert: %s
                - You are now: %s
                - Conversation so far:
                %s
                - Workspace artifacts (read with workspace tools; prefer continuing these files):
                %s
                Instructions: build on prior work. Do not repeat completed steps. If a deliverable already exists, revise or extend it instead of creating duplicates.
                """.formatted(fromLabel, toLabel, dialogue, artifacts);
    }

    public Map<String, Object> switchedEventPayload(
            String fromExpertId,
            String toExpertId,
            int newGeneration,
            String mode) {
        return Map.of(
                "fromExpertId", fromExpertId == null ? "" : fromExpertId,
                "toExpertId", toExpertId,
                "fromExpertName", expertLabel(fromExpertId),
                "toExpertName", expertLabel(toExpertId),
                "newGeneration", newGeneration,
                "mode", mode);
    }

    private String expertLabel(String expertId) {
        if (expertId == null || expertId.isBlank()) {
            return "默认助理";
        }
        return expertService.findExpertDefinition(expertId)
                .map(ExpertDefinition::name)
                .orElse(expertId);
    }

    private String summarizeRecentDialogue(UUID sessionId) {
        List<Map<String, Object>> messages = sessionPersistenceService.listMessages(sessionId);
        List<String> lines = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && lines.size() < MAX_DIALOGUE_LINES; i--) {
            Map<String, Object> message = messages.get(i);
            String kind = stringValue(message.get("kind"));
            if (!"user".equals(kind) && !"assistant".equals(kind)) {
                continue;
            }
            String text = stringValue(message.get("text"));
            if (text.isBlank()) {
                continue;
            }
            String role = "user".equals(kind) ? "用户" : "助理";
            lines.addFirst("  - " + role + ": " + preview(text));
        }
        if (lines.isEmpty()) {
            return "  (无 prior 对话摘要)";
        }
        return String.join("\n", lines);
    }

    private String indexWorkspaceArtifacts(Path workspaceRoot, UUID sessionId) {
        List<String> lines = new ArrayList<>();
        String sessionOfficePrefix = OfficeArtifactContract.sessionTaskRoot(sessionId) + "/";
        artifactService.scanWorkspace(workspaceRoot).stream()
                .limit(MAX_ARTIFACT_LINES * 2L)
                .forEach(entry -> {
                    String path = entry.path();
                    if (path.contains("/.") || path.startsWith("team/")) {
                        return;
                    }
                    boolean officeHit = path.startsWith(sessionOfficePrefix)
                            || path.startsWith(OfficeArtifactContract.ROOT + "/");
                    if (!officeHit && lines.size() >= MAX_ARTIFACT_LINES / 2) {
                        return;
                    }
                    if (lines.size() >= MAX_ARTIFACT_LINES) {
                        return;
                    }
                    lines.add("  - " + path + " (" + entry.size() + " B)");
                });
        if (lines.isEmpty()) {
            return "  (暂无已写入产物)";
        }
        return String.join("\n", lines);
    }

    private static String preview(String text) {
        String oneLine = text.replace('\n', ' ').trim();
        if (oneLine.length() <= LINE_PREVIEW_CHARS) {
            return oneLine;
        }
        return oneLine.substring(0, LINE_PREVIEW_CHARS) + "…";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
