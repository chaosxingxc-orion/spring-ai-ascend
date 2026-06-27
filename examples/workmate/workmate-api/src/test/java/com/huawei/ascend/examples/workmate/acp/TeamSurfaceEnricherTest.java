package com.huawei.ascend.examples.workmate.acp;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamSurfaceEnricherTest {

    @Test
    void enrichesMemberSubRunPayload() {
        UUID sessionId = UUID.randomUUID();
        RunPersistenceContext context = RunPersistenceContext.forMember(
                sessionId, "parent:writer", "parent-run", "writer", "内容写手");

        Map<String, Object> enriched = TeamSurfaceEnricher.enrich(
                context, new LinkedHashMap<>(Map.of("text", "chunk", "toolName", "bash")));

        assertThat(enriched.get("surface")).isEqualTo("team");
        assertThat(enriched.get("memberId")).isEqualTo("writer");
        assertThat(enriched.get("parentRunId")).isEqualTo("parent-run");
        assertThat(enriched.get("subRunId")).isEqualTo("parent:writer");
        assertThat(enriched.get("memberName")).isEqualTo("内容写手");
    }

    @Test
    void leavesParentRunPayloadUntouched() {
        UUID sessionId = UUID.randomUUID();
        RunPersistenceContext context = RunPersistenceContext.forAudit(sessionId, "run-1");

        Map<String, Object> payload = new LinkedHashMap<>(Map.of("text", "hello"));
        Map<String, Object> enriched = TeamSurfaceEnricher.enrich(context, payload);

        assertThat(enriched).doesNotContainKey("surface");
        assertThat(enriched.get("text")).isEqualTo("hello");
    }
}
