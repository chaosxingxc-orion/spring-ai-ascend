package com.huawei.ascend.examples.workmate.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.automation")
public record WorkmateAutomationProperties(long pollIntervalMs, Map<String, WebhookChannelProperties> webhooks) {

    public WorkmateAutomationProperties {
        if (pollIntervalMs <= 0) {
            pollIntervalMs = 60_000L;
        }
        if (webhooks == null) {
            webhooks = new LinkedHashMap<>();
        }
    }

    public record WebhookChannelProperties(boolean enabled, String secret, String defaultExpertId) {

        public WebhookChannelProperties {
            if (defaultExpertId == null) {
                defaultExpertId = "";
            }
        }
    }
}
