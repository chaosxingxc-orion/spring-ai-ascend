package com.huawei.ascend.examples.workmate.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpHttpTargetTest {

    @Test
    void parsesFullYingmiUrl() {
        McpHttpTarget target = McpHttpTarget.parse("https://stargate.yingmi.com/mcp/v2", null);
        assertThat(target.baseUri()).isEqualTo("https://stargate.yingmi.com");
        assertThat(target.endpoint()).isEqualTo("/mcp/v2");
    }

    @Test
    void honorsEndpointOverride() {
        McpHttpTarget target = McpHttpTarget.parse("https://example.com", "/custom/mcp");
        assertThat(target.baseUri()).isEqualTo("https://example.com");
        assertThat(target.endpoint()).isEqualTo("/custom/mcp");
    }
}
