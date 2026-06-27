package com.huawei.ascend.examples.workmate.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorIdsTest {

    @Test
    void normalizesQiemanAlias() {
        assertThat(ConnectorIds.normalize("qieman-mcp")).isEqualTo("qieman");
    }

    @Test
    void normalizesGenericMcpSuffix() {
        assertThat(ConnectorIds.normalize("oa-mcp")).isEqualTo("oa");
    }

    @Test
    void preservesCanonicalIds() {
        assertThat(ConnectorIds.normalize("qieman")).isEqualTo("qieman");
        assertThat(ConnectorIds.normalize("dingtalk")).isEqualTo("dingtalk");
    }

    @Test
    void deduplicatesAfterNormalization() {
        assertThat(ConnectorIds.normalize(List.of("qieman-mcp", "qieman")))
                .containsExactly("qieman");
    }
}
