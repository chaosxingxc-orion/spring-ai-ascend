package com.huawei.ascend.examples.workmate.office;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic office artifact layout and anti-hallucination baseline (W23 / B4).
 */
public final class OfficeArtifactContract {

    public static final String ROOT = "office";
    public static final String TASKS_DIR = "tasks";
    public static final String INPUTS_DIR = "inputs";
    public static final String OUTPUTS_DIR = "outputs";
    public static final String REQUEST_FILE = "request.md";

    public static final String BASELINE_PROMPT = """
            You are an enterprise office assistant. Use only the provided materials and the user's request.

            Rules:
            1. Do not invent facts that are not present in the materials.
            2. If information is missing, list it explicitly.
            3. Produce structured, reviewable output suitable for business use.
            4. Prefer previewable formats: Markdown, table change plans, slide outlines, or checklists.
            5. Do not claim that a file was modified unless the system confirms it was written.
            """;

    private OfficeArtifactContract() {}

    public static boolean isOfficeCapable(ExpertDefinition expert) {
        if (expert == null) {
            return false;
        }
        if (expert.officeCapability() != null && !expert.officeCapability().isBlank()) {
            return true;
        }
        return expert.tags().stream().anyMatch(tag -> "office".equalsIgnoreCase(tag));
    }

    public static String resolveCapability(ExpertDefinition expert) {
        if (expert.officeCapability() != null && !expert.officeCapability().isBlank()) {
            return sanitizeCapability(expert.officeCapability());
        }
        return sanitizeCapability(expert.id());
    }

    public static String taskRoot(String capability, UUID sessionId) {
        return ROOT + "/" + sanitizeCapability(capability) + "/" + sessionId;
    }

    public static String taskRoot(ExpertDefinition expert, UUID sessionId) {
        return taskRoot(resolveCapability(expert), sessionId);
    }

    /** Session-stable office root (W53): all experts in one session share this tree. */
    public static String sessionTaskRoot(UUID sessionId) {
        return ROOT + "/" + TASKS_DIR + "/" + sessionId;
    }

    public static String legacyTaskRoot(ExpertDefinition expert, UUID sessionId) {
        return taskRoot(expert, sessionId);
    }

    public static String inputsDir(String taskRoot) {
        return taskRoot + "/" + INPUTS_DIR;
    }

    public static String outputsDir(String taskRoot) {
        return taskRoot + "/" + OUTPUTS_DIR;
    }

    public static Optional<OfficeArtifactMeta> parseRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }
        String normalized = relativePath.replace('\\', '/');
        if (!normalized.startsWith(ROOT + "/")) {
            return Optional.empty();
        }
        String[] parts = normalized.split("/");
        if (parts.length < 3) {
            return Optional.empty();
        }
        if (TASKS_DIR.equals(parts[1]) && parts.length >= 4) {
            String taskId = parts[2];
            String zone = null;
            if (parts.length >= 5 && (INPUTS_DIR.equals(parts[3]) || OUTPUTS_DIR.equals(parts[3]))) {
                zone = parts[3];
            }
            return Optional.of(new OfficeArtifactMeta(TASKS_DIR, taskId, zone));
        }
        String capability = parts[1];
        String taskId = parts[2];
        String zone = null;
        if (parts.length >= 4) {
            String segment = parts[3];
            if (INPUTS_DIR.equals(segment) || OUTPUTS_DIR.equals(segment)) {
                zone = segment;
            }
        }
        return Optional.of(new OfficeArtifactMeta(capability, taskId, zone));
    }

    public static boolean isUnderInputs(String relativePath) {
        return parseRelativePath(relativePath)
                .map(meta -> INPUTS_DIR.equals(meta.zone()))
                .orElse(false);
    }

    public static Optional<String> validateAgentWritePath(String relativePath) {
        if (isUnderInputs(relativePath)) {
            return Optional.of("Agents must not write to office/*/inputs/. Put drafts in office/{capability}/{taskId}/outputs/.");
        }
        return Optional.empty();
    }

    public static String officeLayoutPrompt(String taskRoot) {
        return """
                Office artifact contract (W23):
                - Task directory: %s/
                - Read user materials from: %s/inputs/
                - Write drafts and deliverables to: %s/outputs/
                - Record structured requirements in: %s/request.md
                - Never overwrite files in inputs/; never claim a file changed until write tools succeed.
                """.formatted(taskRoot, taskRoot, taskRoot, taskRoot);
    }

    private static String sanitizeCapability(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.replaceAll("[^a-z0-9._-]+", "-");
    }

    public record OfficeArtifactMeta(String capability, String taskId, String zone) {}
}
