package com.huawei.ascend.runtime.engine.a2a;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Shared helpers for the A2A SDK {@link Message} model used across engine bridges.
 */
public final class Messages {

    private Messages() {
    }

    /**
     * Extracts the text of all {@link TextPart}s, newline-joined — multiple text
     * parts in one message are distinct paragraphs, and gluing them without a
     * separator would merge words across the part boundary. Tolerates null
     * messages and part lists so wire-facing callers need no pre-checks. This is
     * the single canonical extraction; adapters must not re-implement the loop
     * with divergent join semantics.
     */
    public static String text(Message message) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof TextPart textPart) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(textPart.text());
            }
        }
        return text.toString();
    }
}
