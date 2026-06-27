package com.huawei.ascend.examples.workmate.connector;

import com.huawei.ascend.examples.workmate.office.ConnectorDefinition;

public enum ConnectorAuthMethod {
    NONE,
    API_KEY,
    REDIRECT,
    DEVICE_CODE,
    QR,
    CLI_LOGIN;

    public static ConnectorAuthMethod fromCatalog(String connectorId, boolean requiresAuth) {
        if (!requiresAuth) {
            return NONE;
        }
        return ConnectorCatalog.find(connectorId)
                .map(ConnectorCatalog.ConnectorMeta::authMethod)
                .orElse(API_KEY);
    }

    public static ConnectorAuthMethod fromDefinition(ConnectorDefinition definition) {
        if (definition == null) {
            return NONE;
        }
        ConnectorAuthMethod catalog = ConnectorCatalog.find(definition.id())
                .map(ConnectorCatalog.ConnectorMeta::authMethod)
                .orElse(null);
        if (catalog != null) {
            return catalog;
        }
        return fromYaml(definition.authMethod(), definition.requiresAuth());
    }

    public static ConnectorAuthMethod fromYaml(String authMethod, boolean requiresAuth) {
        if (authMethod == null || authMethod.isBlank()) {
            return requiresAuth ? API_KEY : NONE;
        }
        return switch (authMethod.trim().toUpperCase()) {
            case "NONE" -> NONE;
            case "REDIRECT", "SERVER_SIDE" -> REDIRECT;
            case "DEVICE_CODE" -> DEVICE_CODE;
            case "QR" -> QR;
            case "CLI_LOGIN", "CLI" -> CLI_LOGIN;
            case "MANUAL_TOKEN", "TOKEN", "API_KEY" -> API_KEY;
            default -> requiresAuth ? API_KEY : NONE;
        };
    }
}
