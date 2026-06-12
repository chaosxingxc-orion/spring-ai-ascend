package com.huawei.ascend.agentsdk.example.tools;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CalcDiscountTool {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    private CalcDiscountTool() {
    }

    public static Map<String, Object> calculate(Map<String, Object> inputs) {
        INVOCATIONS.incrementAndGet();
        System.out.println("[agent-sdk-example] CalcDiscountTool.calculate invoked with inputs=" + inputs);
        double amount = number(inputs.get("amount"), 120.0D);
        return Map.of(
                "discount", amount * 0.1D,
                "finalAmount", amount * 0.9D,
                "proof", "calcDiscount-java-tool-executed",
                "inputs", inputs);
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    public static int invocationCount() {
        return INVOCATIONS.get();
    }

    public static void reset() {
        INVOCATIONS.set(0);
    }
}
