package com.huawei.ascend.examples.workmate.connector;

final class CredentialMasker {

    private CredentialMasker() {
    }

    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
