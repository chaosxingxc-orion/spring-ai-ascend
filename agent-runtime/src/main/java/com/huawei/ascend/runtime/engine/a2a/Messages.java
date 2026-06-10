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
     * Extracts the concatenated text of all {@link TextPart}s. Tolerates null
     * messages and part lists so wire-facing callers need no pre-checks.
     */
    public static String text(Message message) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        return text.toString();
    }
}
