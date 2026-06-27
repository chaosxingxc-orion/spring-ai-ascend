package com.huawei.ascend.examples.workmate.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Distinguish leader-terminal run events from member sub-run events on the team surface. */
public final class RunEventTerminal {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunEventTerminal() {}

    public static boolean isTerminal(String eventName, String payloadJson) {
        if ("team.completed".equals(eventName)) {
            return true;
        }
        if ("run.completed".equals(eventName)
                || "run.failed".equals(eventName)
                || "run.error".equals(eventName)) {
            return !isMemberSurfacePayload(payloadJson);
        }
        return false;
    }

    public static boolean isMemberSurfacePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> payload = MAPPER.readValue(payloadJson, new TypeReference<>() {});
            if ("team".equals(payload.get("surface"))) {
                return true;
            }
            Object memberId = payload.get("memberId");
            Object parentRunId = payload.get("parentRunId");
            return memberId instanceof String memberText
                    && !memberText.isBlank()
                    && parentRunId instanceof String parentText
                    && !parentText.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }
}
