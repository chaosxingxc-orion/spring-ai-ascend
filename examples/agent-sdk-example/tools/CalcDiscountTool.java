import java.util.Map;

public final class CalcDiscountTool {
    private CalcDiscountTool() {
    }

    public static Map<String, Object> calculate(Map<String, Object> inputs) {
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
}
