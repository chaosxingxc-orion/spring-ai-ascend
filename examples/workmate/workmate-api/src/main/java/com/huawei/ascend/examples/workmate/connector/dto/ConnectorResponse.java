package com.huawei.ascend.examples.workmate.connector.dto;

public record ConnectorResponse(
        String id,
        String name,
        String description,
        String status,
        int toolCount,
        boolean requiresAuth,
        String authHint,
        String authMethod,
        boolean hasCredential,
        String credentialMask,
        int invalidSchemaCount,
        boolean toolsLimitWarning,
        String lastError,
        boolean runnable,
        String source) {

    public ConnectorResponse(
            String id,
            String name,
            String description,
            String status,
            int toolCount,
            boolean requiresAuth,
            String authHint,
            int invalidSchemaCount,
            boolean toolsLimitWarning,
            String lastError) {
        this(
                id,
                name,
                description,
                status,
                toolCount,
                requiresAuth,
                authHint,
                requiresAuth ? "API_KEY" : "NONE",
                false,
                null,
                invalidSchemaCount,
                toolsLimitWarning,
                lastError,
                true,
                "dogfood");
    }

    public ConnectorResponse(
            String id,
            String name,
            String description,
            String status,
            int toolCount,
            boolean requiresAuth,
            String authHint,
            String authMethod,
            boolean hasCredential,
            String credentialMask,
            int invalidSchemaCount,
            boolean toolsLimitWarning,
            String lastError) {
        this(
                id,
                name,
                description,
                status,
                toolCount,
                requiresAuth,
                authHint,
                authMethod,
                hasCredential,
                credentialMask,
                invalidSchemaCount,
                toolsLimitWarning,
                lastError,
                true,
                "dogfood");
    }
}
