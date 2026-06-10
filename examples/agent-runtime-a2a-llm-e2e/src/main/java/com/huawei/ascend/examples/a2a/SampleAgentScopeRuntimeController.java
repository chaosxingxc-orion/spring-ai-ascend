package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeEvent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeInvocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@ConditionalOnProperty(prefix = "sample.a2a", name = "agent", havingValue = "agentscope")
final class SampleAgentScopeRuntimeController {

    private final AgentScopeAgent agent;

    SampleAgentScopeRuntimeController(@Qualifier("sampleAgentScopeAgent") AgentScopeAgent agent) {
        this.agent = agent;
    }

    @PostMapping(
            value = "/sample/agentscope/process",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    Flux<ServerSentEvent<Map<String, Object>>> process(
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "sample-tenant") String tenantId,
            @RequestHeader(name = "X-Agent-Id", defaultValue = AgentScopeE2eConfiguration.RUNTIME_AGENT_ID) String agentId,
            @RequestHeader(name = "X-Task-Id", defaultValue = "sample-task") String taskId,
            @RequestBody Map<String, Object> body) {
        AgentScopeInvocation invocation = new AgentScopeInvocation(
                tenantId,
                text(body.get("user_id"), "sample-user"),
                text(body.get("session_id"), "sample-session"),
                text(body.get("id"), taskId),
                agentId,
                "USER_MESSAGE",
                messages(body.get("input")),
                map(body.get("variables")),
                map(body.get("metadata")));
        return Flux.fromStream(agent.streamEvents(invocation).map(this::toSse));
    }

    private ServerSentEvent<Map<String, Object>> toSse(AgentScopeEvent event) {
        return ServerSentEvent.builder(toWireEvent(event)).build();
    }

    private Map<String, Object> toWireEvent(AgentScopeEvent event) {
        Map<String, Object> result = new LinkedHashMap<>();
        switch (event.type()) {
            case OUTPUT -> {
                result.put("status", "output");
                result.put("text", event.text());
            }
            case COMPLETED -> {
                result.put("status", "completed");
                result.put("text", event.text());
            }
            case FAILED -> {
                result.put("status", "error");
                result.put("error_code", event.errorCode());
                result.put("message", event.errorMessage());
            }
            case INTERRUPTED -> {
                result.put("status", "interrupted");
                result.put("text", event.text());
            }
        }
        return result;
    }

    private List<Message> messages(Object rawInput) {
        if (!(rawInput instanceof List<?> list)) {
            return List.of(message(Message.Role.ROLE_USER, ""));
        }
        List<Message> result = new ArrayList<>();
        for (Object rawMessage : list) {
            if (!(rawMessage instanceof Map<?, ?> message)) {
                continue;
            }
            result.add(message(role(message.get("role")), contentText(message.get("content"))));
        }
        return result.isEmpty() ? List.of(message(Message.Role.ROLE_USER, "")) : List.copyOf(result);
    }

    private Message message(Message.Role role, String text) {
        return Message.builder()
                .role(role)
                .parts(List.of(new TextPart(text)))
                .build();
    }

    private Message.Role role(Object raw) {
        String value = text(raw, "user");
        if ("assistant".equalsIgnoreCase(value) || "agent".equalsIgnoreCase(value)) {
            return Message.Role.ROLE_AGENT;
        }
        return Message.Role.ROLE_USER;
    }

    private String contentText(Object content) {
        if (content instanceof String text) {
            return text;
        }
        if (!(content instanceof List<?> parts)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Object rawPart : parts) {
            if (rawPart instanceof Map<?, ?> part) {
                result.append(text(part.get("text"), ""));
            }
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String text(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw);
        return value.isBlank() ? fallback : value;
    }
}
