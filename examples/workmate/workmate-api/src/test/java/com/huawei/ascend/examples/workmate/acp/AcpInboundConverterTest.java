package com.huawei.ascend.examples.workmate.acp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcpInboundConverterTest {

    private final AcpInboundConverter inbound = new AcpInboundConverter();
    private final AcpOutboundConverter outbound = new AcpOutboundConverter();

    @Test
    void mapsAgentMessageChunkToMessageDelta() {
        RunEventDraft draft = inbound.convert(Map.of(
                "sessionUpdate", "agent_message_chunk",
                "content", Map.of("text", "hello"),
                "_meta", Map.of(AcpMetaKeys.OFFSET, 3)));

        assertThat(draft.eventName()).isEqualTo("message.delta");
        assertThat(draft.payload().get("text")).isEqualTo("hello");
    }

    @Test
    void mapsMemberToolCallWithTeamSurface() {
        RunEventDraft draft = inbound.convert(Map.of(
                "sessionUpdate", "tool_call",
                "content", Map.of(
                        "toolName", "workmate_read",
                        "toolCallId", "call-1"),
                "_meta", Map.of(
                        AcpMetaKeys.MEMBER_EVENT, "PRD 写手",
                        AcpMetaKeys.MEMBER_ID, "prd-writer",
                        AcpMetaKeys.PARENT_RUN_ID, "parent-run",
                        "surface", "team")));

        assertThat(draft.eventName()).isEqualTo("tool.start");
        assertThat(draft.payload().get("surface")).isEqualTo("team");
        assertThat(draft.payload().get("memberName")).isEqualTo("PRD 写手");
        assertThat(draft.payload().get("memberId")).isEqualTo("prd-writer");
    }

    @Test
    void mapsOpenResultViewToArtifactAdded() {
        RunEventDraft draft = inbound.convert(Map.of(
                "sessionUpdate", "open_result_view",
                "content", Map.of(
                        "path", "outputs/index.html",
                        "name", "index.html",
                        "mime", "text/html",
                        "openInPanel", true,
                        "preferredTab", "browser")));

        assertThat(draft.eventName()).isEqualTo("artifact.added");
        assertThat(draft.payload().get("openInPanel")).isEqualTo(true);
    }

    @Test
    void roundTripsOutboundMessageDelta() {
        Map<String, Object> acp = outbound.convertEntry(Map.of(
                "seq", 5,
                "name", "message.delta",
                "data", Map.of("text", "chunk"))).toMap();

        RunEventDraft draft = inbound.convert(acp);
        assertThat(draft.eventName()).isEqualTo("message.delta");
        assertThat(draft.payload().get("text")).isEqualTo("chunk");
    }

    @Test
    void accumulatorMergesChunks() {
        AcpMessageAccumulator accumulator = new AcpMessageAccumulator();
        List<RunEventDraft> first = accumulator.ingest(Map.of(
                "sessionUpdate", "agent_message_chunk",
                "content", Map.of("text", "hel")));
        List<RunEventDraft> second = accumulator.ingest(Map.of(
                "sessionUpdate", "agent_message_chunk",
                "content", Map.of("text", "lo")));
        assertThat(first).isEmpty();
        assertThat(second).isEmpty();

        List<RunEventDraft> flushed = accumulator.flush();
        assertThat(flushed).hasSize(1);
        assertThat(flushed.get(0).eventName()).isEqualTo("message.delta");
        assertThat(flushed.get(0).payload().get("text")).isEqualTo("hello");
    }

    @Test
    void roundTripsMemberToolCallMetaFromInboundFixtureShape() {
        Map<String, Object> acp = Map.of(
                "sessionUpdate", "tool_call",
                "content", Map.of(
                        "toolName", "workmate_read",
                        "toolCallId", "call-1",
                        "args", Map.of("path", "notes.md")),
                "_meta", Map.of(
                        AcpMetaKeys.MEMBER_EVENT, "software-engineer",
                        AcpMetaKeys.MEMBER_ID, "software-engineer",
                        AcpMetaKeys.PARENT_RUN_ID, "parent-run",
                        "surface", "team"));

        RunEventDraft draft = inbound.convert(acp);
        assertThat(draft.eventName()).isEqualTo("tool.start");
        assertThat(draft.payload().get("surface")).isEqualTo("team");
        assertThat(draft.payload().get("memberId")).isEqualTo("software-engineer");

        Map<String, Object> outboundAgain = outbound.convertEntry(Map.of(
                "seq", 7,
                "name", draft.eventName(),
                "data", draft.payload())).toMap();
        assertThat(outboundAgain.get("_meta")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) outboundAgain.get("_meta");
        assertThat(meta.get("surface")).isEqualTo("team");
        assertThat(meta.get(AcpMetaKeys.MEMBER_ID)).isEqualTo("software-engineer");
    }

    @Test
    void accumulatorFlushesBeforeNonChunkEvent() {
        AcpMessageAccumulator accumulator = new AcpMessageAccumulator();
        List<RunEventDraft> drafts = accumulator.ingestAll(List.of(
                Map.of("sessionUpdate", "agent_message_chunk", "content", Map.of("text", "hi")),
                Map.of(
                        "sessionUpdate", "tool_call",
                        "content", Map.of("toolName", "bash", "toolCallId", "c1"))));

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).eventName()).isEqualTo("message.delta");
        assertThat(drafts.get(0).payload().get("text")).isEqualTo("hi");
        assertThat(drafts.get(1).eventName()).isEqualTo("tool.start");
    }
}
