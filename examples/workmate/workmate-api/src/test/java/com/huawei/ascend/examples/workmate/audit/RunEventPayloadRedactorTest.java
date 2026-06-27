package com.huawei.ascend.examples.workmate.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateAuditProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunEventPayloadRedactorTest {

    private final RunEventPayloadRedactor redactor =
            new RunEventPayloadRedactor(new ObjectMapper(), new WorkmateAuditProperties(200, 16384, true));

    @Test
    void redactsSecretKeys() {
        Map<String, Object> payload = Map.of("apiKey", "sk-secret", "toolName", "mcp__docs");
        Map<String, Object> redacted = redactor.redact("tool.start", payload);

        assertThat(redacted.get("apiKey")).isEqualTo("[REDACTED]");
        assertThat(redacted.get("toolName")).isEqualTo("mcp__docs");
    }

    @Test
    void truncatesLargeTextWithPreviewAndHash() {
        String longText = "x".repeat(300);
        Map<String, Object> payload = Map.of("text", longText);
        Map<String, Object> redacted = redactor.redact("message.delta", payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> textField = (Map<String, Object>) redacted.get("text");
        assertThat(textField.get("bytes")).isEqualTo(300);
        assertThat(textField.get("sha256")).isNotNull();
        assertThat(textField.get("preview").toString()).hasSize(201);
    }

    @Test
    void redactsEmailInFreeText() {
        Map<String, Object> payload = Map.of("summary", "contact user@example.com please");
        Map<String, Object> redacted = redactor.redact("team.member.completed", payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) redacted.get("summary");
        assertThat(summary.get("preview")).asString().contains("[REDACTED_EMAIL]");
    }

    @Test
    void keepsFullDelegationTaskMessageForSendMessageTool() {
        String task = "## 任务：整合并输出最终研究报告\n".repeat(40); // > 200 chars
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("memberId", "report-publisher");
        args.put("message", task);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", "team.send_message");
        payload.put("args", args);

        Map<String, Object> redacted = redactor.redact("tool.start", payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> redactedArgs = (Map<String, Object>) redacted.get("args");
        // The task prompt survives intact (a plain string), not clipped to a {preview,bytes} object.
        assertThat(redactedArgs.get("message")).isEqualTo(task);
    }

    @Test
    void stillTruncatesMessageForNonDelegationEvents() {
        Map<String, Object> payload = Map.of("message", "z".repeat(300));
        Map<String, Object> redacted = redactor.redact("message.delta", payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> messageField = (Map<String, Object>) redacted.get("message");
        assertThat(messageField.get("preview").toString()).hasSize(201);
    }

    @Test
    void preservesMemberHandbackRoutingWhenArgsAreJsonString() {
        String argsJson =
                "{\"to\":\"team-lead\",\"recipient\":\"team-lead\",\"content\":\""
                        + "x".repeat(300)
                        + "\",\"summary\":\"Phase done\"}";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", "send_message");
        payload.put("memberId", "topic-researcher");
        payload.put("args", argsJson);

        Map<String, Object> redacted = redactor.redact("tool.start", payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> redactedArgs = (Map<String, Object>) redacted.get("args");
        assertThat(redactedArgs.get("to")).isEqualTo("team-lead");
        assertThat(redactedArgs.get("recipient")).isEqualTo("team-lead");
        assertThat(redactedArgs.get("content")).isInstanceOf(String.class);
        assertThat(((String) redactedArgs.get("content")).length()).isGreaterThanOrEqualTo(300);
        @SuppressWarnings("unchecked")
        Map<String, Object> summaryField = (Map<String, Object>) redactedArgs.get("summary");
        assertThat(summaryField.get("preview")).isEqualTo("Phase done");
    }

    @Test
    void truncatesOversizedPayloadEnvelope() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("blob", "y".repeat(20000));
        Map<String, Object> redacted = redactor.redact("tool.end", payload);

        assertThat(redacted.get("metadata_truncated")).isEqualTo(true);
        assertThat(redacted.get("metadata_original_bytes")).isNotNull();
    }
}
