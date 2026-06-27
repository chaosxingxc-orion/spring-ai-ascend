package com.huawei.ascend.examples.workmate.acp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcpOutboundConverterTest {

    private final AcpOutboundConverter converter = new AcpOutboundConverter();

    @Test
    void mapsMessageDeltaToAgentMessageChunk() {
        Map<String, Object> acp = converter.convertEntry(Map.of(
                "seq", 3,
                "name", "message.delta",
                "data", Map.of("text", "hello"))).toMap();

        assertThat(acp.get("sessionUpdate")).isEqualTo("agent_message_chunk");
        assertThat(((Map<?, ?>) acp.get("content")).get("text")).isEqualTo("hello");
        assertThat(((Map<?, ?>) acp.get("_meta")).get(AcpMetaKeys.OFFSET)).isEqualTo(3);
    }

    @Test
    void mapsMemberToolStartWithMemberEventMeta() {
        Map<String, Object> acp = converter.convertEntry(Map.of(
                "seq", 7,
                "name", "tool.start",
                "data", Map.of(
                        "toolName", "workmate_read",
                        "toolCallId", "call-1",
                        "memberId", "prd-writer",
                        "memberName", "PRD 写手",
                        "parentRunId", "parent-run"))).toMap();

        assertThat(acp.get("sessionUpdate")).isEqualTo("tool_call");
        Map<?, ?> meta = (Map<?, ?>) acp.get("_meta");
        assertThat(meta.get(AcpMetaKeys.MEMBER_EVENT)).isEqualTo("PRD 写手");
        assertThat(meta.get("surface")).isEqualTo("team");
    }

    @Test
    void mapsArtifactAddedToOpenResultView() {
        Map<String, Object> acp = converter.convertEntry(Map.of(
                "seq", 9,
                "name", "artifact.added",
                "data", Map.of(
                        "path", "outputs/index.html",
                        "name", "index.html",
                        "mime", "text/html",
                        "openInPanel", true,
                        "preferredTab", "browser"))).toMap();

        assertThat(acp.get("sessionUpdate")).isEqualTo("open_result_view");
        Map<?, ?> content = (Map<?, ?>) acp.get("content");
        assertThat(content.get("path")).isEqualTo("outputs/index.html");
        assertThat(content.get("openInPanel")).isEqualTo(true);
    }

    @Test
    void convertsEventLogInOrder() {
        List<Map<String, Object>> acpLog = converter.convertEventLog(List.of(
                Map.of("seq", 1, "name", "tool.start", "data", Map.of("toolName", "bash", "toolCallId", "c1")),
                Map.of("seq", 2, "name", "tool.end", "data", Map.of("toolName", "bash", "toolCallId", "c1", "result", Map.of("success", true)))));

        assertThat(acpLog).hasSize(2);
        assertThat(acpLog.get(0).get("sessionUpdate")).isEqualTo("tool_call");
        assertThat(acpLog.get(1).get("sessionUpdate")).isEqualTo("tool_call_update");
    }
}
