package com.huawei.ascend.examples.workmate.agent.dto;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public sealed interface PromptRunResult {

    record Started(SseEmitter emitter) implements PromptRunResult {}

    record Queued(int queuePosition, int queueDepth) implements PromptRunResult {}
}
