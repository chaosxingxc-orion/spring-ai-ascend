package com.huawei.ascend.examples.workmate.connector.dto;

public record ConnectorAuthProfileResponse(
        String connectorId,
        String authMethod,
        boolean hasCredential,
        String credentialMask) {}
