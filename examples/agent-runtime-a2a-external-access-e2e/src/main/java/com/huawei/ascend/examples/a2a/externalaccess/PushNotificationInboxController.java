package com.huawei.ascend.examples.a2a.externalaccess;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PushNotificationInboxController {
    private final PushNotificationInbox inbox;

    public PushNotificationInboxController(PushNotificationInbox inbox) {
        this.inbox = inbox;
    }

    @PostMapping("/test/push-notifications")
    public ResponseEntity<Void> receive(@RequestBody String payload) {
        inbox.record(payload);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/test/push-notifications")
    public ResponseEntity<List<String>> list() {
        return ResponseEntity.ok(inbox.payloads());
    }
}
