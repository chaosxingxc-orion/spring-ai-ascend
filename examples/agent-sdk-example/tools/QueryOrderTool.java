import java.util.Map;

public final class QueryOrderTool {
    private QueryOrderTool() {
    }

    public static Map<String, Object> query(Map<String, Object> inputs) {
        System.out.println("[agent-sdk-example] QueryOrderTool.query invoked with inputs=" + inputs);
        Object orderId = inputs.getOrDefault("orderId", "A-10086");
        return Map.of(
                "orderId", orderId,
                "status", "PAID",
                "amount", 1280,
                "proof", "queryOrder-java-tool-executed",
                "inputs", inputs);
    }
}
