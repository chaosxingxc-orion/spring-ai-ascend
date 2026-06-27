package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class McpJsonConfigMapperTest {

    private final McpJsonConfigMapper mapper = new McpJsonConfigMapper(new ObjectMapper());
    private final Path officeRoot = Path.of("../office").toAbsolutePath().normalize();

    @Test
    void parsesStreamableHttpConnector() {
        Path dir = officeRoot.resolve("connectors-market/github");
        McpServerConfig config = mapper.parse("github", dir, "mcp.json", false).orElseThrow();
        assertThat(config.id()).isEqualTo("github");
        assertThat(config.url()).contains("githubcopilot.com");
        assertThat(config.transport()).isEqualTo("streamable-http");
        assertThat(config.command()).isNull();
    }

    @Test
    void parsesStdioConnectorWithEnv() {
        Path dir = officeRoot.resolve("connectors-market/weisheng-scrm");
        McpServerConfig config = mapper.parse("weisheng-scrm", dir, "mcp.json", false).orElseThrow();
        assertThat(config.transport()).isEqualTo("stdio");
        assertThat(config.command()).isEqualTo("npx");
        assertThat(config.env()).containsKey("SCRM_BASE_URL");
        assertThat(config.timeoutSeconds()).isEqualTo(120L);
    }
}
