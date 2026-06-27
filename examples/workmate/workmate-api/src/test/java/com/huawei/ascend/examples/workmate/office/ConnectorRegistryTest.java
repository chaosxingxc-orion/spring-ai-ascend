package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import org.junit.jupiter.api.Test;

class ConnectorRegistryTest {

    @Test
    void loadsMarketConnectorsFromOffice() {
        ConnectorRegistry registry = new ConnectorRegistry(
                new WorkmateOfficeProperties("../office"),
                new McpJsonConfigMapper(new com.fasterxml.jackson.databind.ObjectMapper()));
        assertThat(registry.listConnectors()).isNotEmpty();
        assertThat(registry.findConnector("github")).isPresent();
        assertThat(registry.findConnector("github").orElseThrow().runnable()).isTrue();
        assertThat(registry.findConnector("notion")).isPresent();
    }
}
