package com.huawei.ascend.examples.workmate.prompt;

import com.huawei.ascend.examples.workmate.agent.AgentRunService;
import com.huawei.ascend.examples.workmate.agent.dto.PromptRunResult;
import com.huawei.ascend.examples.workmate.prompt.dto.PromptRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/sessions")
public class PromptController {

    private final AgentRunService agentRunService;

    public PromptController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @PostMapping(
            value = "/{id}/prompt",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object prompt(@PathVariable UUID id, @Valid @RequestBody PromptRequest request) {
        PromptRunResult result = agentRunService.runPrompt(id, request);
        if (result instanceof PromptRunResult.Queued queued) {
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "status",
                            "queued",
                            "queuePosition",
                            queued.queuePosition(),
                            "queueDepth",
                            queued.queueDepth()));
        }
        return ((PromptRunResult.Started) result).emitter();
    }

    @PostMapping(value = "/{id}/messages/{seq}/edit", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter editMessage(
            @PathVariable UUID id, @PathVariable int seq, @Valid @RequestBody PromptRequest request) {
        return agentRunService.editMessage(id, seq, request.message());
    }

    @PostMapping(value = "/{id}/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retry(@PathVariable UUID id) {
        return agentRunService.retry(id);
    }
}
