package com.huawei.ascend.agentsdk.example.tools;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class QueryOrderTool {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    private QueryOrderTool() {
    }

    public static Map<String, Object> query(Map<String, Object> inputs) {
        INVOCATIONS.incrementAndGet();
        System.out.println("[agent-sdk-example] QueryOrderTool.query invoked with inputs=" + inputs);
        Object orderId = inputs.getOrDefault("orderId", "A-10086");
        return Map.of(
                "orderId", orderId,
                "status", "PAID",
                "amount", 1280,
                "proof", "queryOrder-java-tool-executed",
                "inputs", inputs);
    }

    public static int invocationCount() {
        return INVOCATIONS.get();
    }

    public static void reset() {
        INVOCATIONS.set(0);
    }
}
