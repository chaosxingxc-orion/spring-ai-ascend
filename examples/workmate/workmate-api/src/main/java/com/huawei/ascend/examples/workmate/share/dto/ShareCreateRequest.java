package com.huawei.ascend.examples.workmate.share.dto;

/** W49-E2 — optional scope and expiry when creating a share link. */
public record ShareCreateRequest(String scope, Integer expiresInHours) {

    public String normalizedScope() {
        if (scope == null || scope.isBlank()) {
            return "full";
        }
        return switch (scope.trim().toLowerCase()) {
            case "messages", "conversation" -> "messages";
            case "artifacts" -> "artifacts";
            default -> "full";
        };
    }

    public int normalizedExpiresInHours() {
        if (expiresInHours == null || expiresInHours <= 0) {
            return 168;
        }
        return Math.min(expiresInHours, 24 * 90);
    }
}
