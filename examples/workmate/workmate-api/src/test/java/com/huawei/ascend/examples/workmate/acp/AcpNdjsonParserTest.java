package com.huawei.ascend.examples.workmate.acp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcpNdjsonParserTest {

    private final AcpNdjsonParser parser = new AcpNdjsonParser(new ObjectMapper());

    @Test
    void parsesMultipleLines() {
        String ndjson =
                """
                {"sessionUpdate":"agent_message_chunk","content":{"text":"hel"}}
                {"sessionUpdate":"agent_message_chunk","content":{"text":"lo"}}
                """;

        List<Map<String, Object>> rows = parser.parse(ndjson);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("sessionUpdate")).isEqualTo("agent_message_chunk");
    }

    @Test
    void rejectsInvalidJsonLine() {
        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
