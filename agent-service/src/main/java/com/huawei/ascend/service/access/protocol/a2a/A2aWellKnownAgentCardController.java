package com.huawei.ascend.service.access.protocol.a2a;

import java.util.Objects;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class A2aWellKnownAgentCardController {

    private final AgentCard agentCard;

    public A2aWellKnownAgentCardController(AgentCard agentCard) {
        this.agentCard = Objects.requireNonNull(agentCard, "agentCard");
    }

    @GetMapping("/.well-known/agent-card.json")
    public ResponseEntity<AgentCard> getAgentCard() {
        return ResponseEntity.ok(agentCard);
    }
}




