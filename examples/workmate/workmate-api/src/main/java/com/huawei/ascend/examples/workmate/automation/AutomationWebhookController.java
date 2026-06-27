package com.huawei.ascend.examples.workmate.automation;

import com.huawei.ascend.examples.workmate.automation.dto.WebhookConfigResponse;
import com.huawei.ascend.examples.workmate.automation.dto.WebhookDeliveryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automation/webhooks")
public class AutomationWebhookController {

    private final AutomationWebhookService automationWebhookService;
    private final ObjectMapper objectMapper;

    public AutomationWebhookController(
            AutomationWebhookService automationWebhookService, ObjectMapper objectMapper) {
        this.automationWebhookService = automationWebhookService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/config")
    public WebhookConfigResponse config() {
        return automationWebhookService.getConfig();
    }

    @GetMapping("/deliveries")
    public List<WebhookDeliveryResponse> deliveries(@RequestParam(defaultValue = "20") int limit) {
        return automationWebhookService.listDeliveries(limit);
    }

    @PostMapping("/{channel}")
    public Object receive(
            @PathVariable String channel,
            @RequestHeader(value = "X-WorkMate-Webhook-Secret", required = false) String secret,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode node = body == null ? null : objectMapper.valueToTree(body);
        return automationWebhookService.handle(channel, secret, node);
    }
}
