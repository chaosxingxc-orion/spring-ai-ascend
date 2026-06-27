package com.huawei.ascend.examples.workmate.team.mailbox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Result of routing a member {@code send_message} through {@link com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool}. */
public record MemberSendMessageOutcome(String fromId, String resolvedRecipient, String summary) {

    public Map<String, Object> toToolResult() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "message");
        data.put("from", fromId);
        data.put("to", resolvedRecipient);
        data.put("recipient", resolvedRecipient);
        if (summary != null && !summary.isBlank()) {
            data.put("summary", summary);
        }
        data.put("resultMessage", "Delivered");
        return Map.of("success", true, "data", data);
    }
}
