package com.huawei.ascend.examples.workmate.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.automation.dto.WebhookDeliveryResponse;
import com.huawei.ascend.examples.workmate.automation.dto.WebhookTriggerResponse;
import com.huawei.ascend.examples.workmate.automation.dto.WebhookChannelConfigResponse;
import com.huawei.ascend.examples.workmate.automation.dto.WebhookConfigResponse;
import com.huawei.ascend.examples.workmate.config.WorkmateAutomationProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateAutomationProperties.WebhookChannelProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.agent.AgentRunService;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionRequest;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AutomationWebhookService {

    private final WorkmateAutomationProperties automationProperties;
    private final WorkmateLlmProperties llm;
    private final SessionService sessionService;
    private final AgentRunService agentRunService;
    private final ObjectMapper objectMapper;
    private final WebhookDeliveryService webhookDeliveryService;

    public AutomationWebhookService(
            WorkmateAutomationProperties automationProperties,
            WorkmateLlmProperties llm,
            SessionService sessionService,
            AgentRunService agentRunService,
            ObjectMapper objectMapper,
            WebhookDeliveryService webhookDeliveryService) {
        this.automationProperties = automationProperties;
        this.llm = llm;
        this.sessionService = sessionService;
        this.agentRunService = agentRunService;
        this.objectMapper = objectMapper;
        this.webhookDeliveryService = webhookDeliveryService;
    }

    public WebhookConfigResponse getConfig() {
        List<WebhookChannelConfigResponse> channels = automationProperties.webhooks().entrySet().stream()
                .map(entry -> new WebhookChannelConfigResponse(
                        entry.getKey(),
                        entry.getValue().enabled(),
                        "/api/v1/automation/webhooks/" + entry.getKey(),
                        StringUtils.hasText(entry.getValue().secret())))
                .toList();
        return new WebhookConfigResponse(channels);
    }

    public List<WebhookDeliveryResponse> listDeliveries(int limit) {
        return webhookDeliveryService.listRecent(limit);
    }

    public Object handle(String channel, String providedSecret, JsonNode body) {
        try {
            WebhookChannelProperties config = resolveChannel(channel);
            if (!config.enabled()) {
                throw new IllegalArgumentException("Webhook channel disabled: " + channel);
            }
            verifySecret(config, providedSecret, body);

            if (body != null && body.hasNonNull("challenge")) {
                webhookDeliveryService.record(channel, "CHALLENGE", null, "challenge handshake");
                Map<String, String> challenge = new LinkedHashMap<>();
                challenge.put("challenge", body.get("challenge").asText());
                return challenge;
            }

            String text = extractText(channel, body);
            if (!StringUtils.hasText(text)) {
                throw new IllegalArgumentException("Webhook payload missing message text");
            }

            if (!llm.isConfigured()) {
                WebhookTriggerResponse skipped =
                        new WebhookTriggerResponse(null, "SKIPPED", "LLM not configured");
                webhookDeliveryService.record(channel, skipped.status(), null, skipped.message());
                return skipped;
            }

            String title = extractTitle(body, channel);
            String expertId = extractExpertId(body, config);
            CreateSessionResponse created = sessionService.createSession(new CreateSessionRequest(
                    title,
                    null,
                    expertId,
                    PermissionMode.CRAFT,
                    null,
                    null,
                    true,
                    null,
                    null,
                    null));
            UUID sessionId = created.session().id();
            agentRunService.runPromptFireAndForget(sessionId, text.trim());
            WebhookTriggerResponse started =
                    new WebhookTriggerResponse(sessionId, "STARTED", "Session created and prompt dispatched");
            webhookDeliveryService.record(channel, started.status(), started.sessionId(), started.message());
            return started;
        } catch (RuntimeException ex) {
            webhookDeliveryService.record(channel, "ERROR", null, ex.getMessage());
            throw ex;
        }
    }

    private WebhookChannelProperties resolveChannel(String channel) {
        WebhookChannelProperties config = automationProperties.webhooks().get(channel);
        if (config == null) {
            throw new IllegalArgumentException("Unknown webhook channel: " + channel);
        }
        return config;
    }

    private void verifySecret(WebhookChannelProperties config, String headerSecret, JsonNode body) {
        String expected = config.secret();
        // An enabled channel MUST have a configured secret; never run "open".
        if (!StringUtils.hasText(expected)) {
            throw new IllegalStateException(
                    "Webhook channel is enabled but no secret is configured; refusing to process request");
        }
        // Constant-time comparison avoids leaking the secret via response timing. The secret must be
        // presented in a header; request-body tokens are only accepted for the provider URL-verification
        // challenge handshake (which carries no side effects).
        if (StringUtils.hasText(headerSecret) && constantTimeEquals(expected, headerSecret)) {
            return;
        }
        if (body != null && body.hasNonNull("challenge")) {
            if (body.has("token") && constantTimeEquals(expected, body.get("token").asText())) {
                return;
            }
            if (body.path("header").has("token")
                    && constantTimeEquals(expected, body.path("header").get("token").asText())) {
                return;
            }
        }
        throw new IllegalArgumentException("Invalid webhook secret");
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String extractTitle(JsonNode body, String channel) {
        if (body != null && body.hasNonNull("title")) {
            return body.get("title").asText();
        }
        return switch (channel) {
            case "feishu" -> "飞书 Webhook";
            case "wecom" -> "企微 Webhook";
            default -> "IM Webhook";
        };
    }

    private String extractExpertId(JsonNode body, WebhookChannelProperties config) {
        if (body != null && body.hasNonNull("expertId")) {
            return body.get("expertId").asText();
        }
        return StringUtils.hasText(config.defaultExpertId()) ? config.defaultExpertId() : null;
    }

    private String extractText(String channel, JsonNode body) {
        if (body == null) {
            return null;
        }
        if (body.hasNonNull("text")) {
            return body.get("text").asText();
        }
        if ("feishu".equals(channel)) {
            JsonNode content = body.path("event").path("message").path("content");
            if (!content.isMissingNode()) {
                try {
                    JsonNode parsed = objectMapper.readTree(content.asText());
                    if (parsed.has("text")) {
                        return parsed.get("text").asText();
                    }
                } catch (Exception ignored) {
                    return content.asText();
                }
            }
        }
        if ("wecom".equals(channel) && body.has("Content")) {
            return body.get("Content").asText();
        }
        return null;
    }
}
