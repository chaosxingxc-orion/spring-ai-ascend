package com.huawei.ascend.examples.workmate.office;

import java.util.regex.Pattern;

final class OfficeImportValidator {

    static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,127}$");
    static final Pattern SAFE_FILE = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");

    private OfficeImportValidator() {
    }

    /**
     * Validates a draft file name (e.g. {@code prompt.md}, {@code SKILL.md}). Rejects any path
     * separator, parent-dir token, or absolute path so a request body cannot escape the draft dir.
     */
    static String requireSafeFileName(String name, String label) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(label + " required");
        }
        String trimmed = name.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")
                || !SAFE_FILE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(label + " must be a safe file name: " + trimmed);
        }
        return trimmed;
    }

    static String requireSafeId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(label + " id required");
        }
        String trimmed = id.trim();
        if (!SAFE_ID.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(label + " id must match [a-z0-9_-]: " + trimmed);
        }
        return trimmed;
    }

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " required");
        }
        return value.trim();
    }
}
