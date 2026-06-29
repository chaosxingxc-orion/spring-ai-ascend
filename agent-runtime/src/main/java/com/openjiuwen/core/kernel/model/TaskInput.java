package com.openjiuwen.core.kernel.model;

import java.util.Map;

/**
 * 任务输入。
 * 包含用户的原始输入、上下文参数和元数据。
 *
 * @param userInput  用户原始输入文本
 * @param parameters 结构化参数（可选，由开发者传入）
 * @param metadata   元数据（sessionId, userId, traceId 等）
 */
public record TaskInput(
    String userInput,
    Map<String, Object> parameters,
    Map<String, String> metadata
) {

    /** 创建只有文本输入的简单 TaskInput */
    public static TaskInput of(String userInput) {
        return new TaskInput(userInput, Map.of(), Map.of());
    }

    /** 创建带参数的 TaskInput */
    public static TaskInput of(String userInput, Map<String, Object> parameters) {
        return new TaskInput(userInput, parameters, Map.of());
    }
}
