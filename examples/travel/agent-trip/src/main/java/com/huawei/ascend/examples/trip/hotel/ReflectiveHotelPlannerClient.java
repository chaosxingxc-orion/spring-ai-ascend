/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.hotel;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;

import java.util.Map;
import java.util.UUID;

/**
 * 反射委托的酒店出站客户端：通过反射加载 agent-hotel 的
 * {@code com.huawei.ascend.examples.hotel.HotelPlanAgentBuilder} 构建其 ReActAgent，
 * {@link #plan(String)} 时跑一轮 ReAct 返回 markdown。
 *
 * <p>本类只引用 openJiuwen 共享类型（{@link ReActAgent}/{@link Runner}）与反射 API，
 * <b>不出现任何 agent-hotel 的编译期类型</b>，因此 agent-trip 对 agent-hotel 无编译期依赖
 * （pom 中 agent-hotel 为 {@code runtime} scope，仅运行时 classpath 需要）。
 *
 * <p>这与主规划智能体反射装配行程智能体（{@code Main.buildTripAgent}）是同一套手法。
 */
public class ReflectiveHotelPlannerClient implements HotelPlannerClient {

    private static final String HOTEL_BUILDER_CLASS =
            "com.huawei.ascend.examples.hotel.HotelPlanAgentBuilder";

    private final ReActAgent hotelAgent;

    public ReflectiveHotelPlannerClient(String provider, String apiKey, String apiBase,
                                        String modelName, boolean verifySsl) {
        this.hotelAgent = buildHotelAgent(provider, apiKey, apiBase, modelName, verifySsl);
    }

    @Override
    public String plan(String naturalLanguageRequest) {
        String conversationId = "hotel-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            Object raw = Runner.runAgent(
                    hotelAgent,
                    Map.of("query", naturalLanguageRequest, "conversation_id", conversationId),
                    null,
                    null);
            return extractOutput(raw);
        } finally {
            Runner.release(conversationId);
        }
    }

    private static ReActAgent buildHotelAgent(String provider, String apiKey, String apiBase,
                                              String modelName, boolean verifySsl) {
        try {
            Class<?> builderClass = Class.forName(HOTEL_BUILDER_CLASS);
            Object builder = builderClass.getMethod("builder").invoke(null);
            builder = builderClass.getMethod("modelClient", String.class, String.class,
                            String.class, String.class, boolean.class)
                    .invoke(builder, provider, apiKey, apiBase, modelName, verifySsl);
            Object agent = builderClass.getMethod("build").invoke(builder);
            return (ReActAgent) agent;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "找不到酒店子智能体入口类: " + HOTEL_BUILDER_CLASS
                            + "。请确认 agent-hotel 已在运行时 classpath（pom 的 runtime 依赖或手动加 jar）。", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射构建酒店子智能体失败：" + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractOutput(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object output = ((Map<String, Object>) map).get("output");
            if (output != null) {
                return String.valueOf(output);
            }
        }
        return raw == null ? "" : String.valueOf(raw);
    }
}
