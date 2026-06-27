package com.huawei.ascend.examples.workmate.automation;

import com.huawei.ascend.examples.workmate.automation.dto.WebhookDeliveryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDeliveryService {

    private final WebhookDeliveryRepository repository;

    public WebhookDeliveryService(WebhookDeliveryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String channel, String outcome, UUID sessionId, String message) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setChannel(channel);
        delivery.setOutcome(outcome);
        delivery.setSessionId(sessionId);
        delivery.setMessage(trimMessage(message));
        repository.save(delivery);
    }

    public List<WebhookDeliveryResponse> listRecent(int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(WebhookDeliveryResponse::from)
                .toList();
    }

    private static String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2048 ? message : message.substring(0, 2048);
    }
}
