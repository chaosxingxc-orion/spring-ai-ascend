package com.huawei.ascend.examples.workmate.connector;

public class ConnectorNotFoundException extends RuntimeException {

    public ConnectorNotFoundException(String connectorId) {
        super("Connector not found: " + connectorId);
    }
}
