package com.huawei.ascend.examples.runtime.middleware.mcp.localtime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class LocalTimeMcpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalTimeMcpServerApplication.class, args);
    }
}

@RestController
class LocalTimeMcpController {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    @PostMapping("/mcp")
    Map<String, Object> mcp(@RequestBody Map<String, Object> request) {
        Object id = request.get("id");
        String method = String.valueOf(request.get("method"));
        return switch (method) {
            case "initialize" -> response(id, Map.of(
                    "protocolVersion", "2025-06-18",
                    "serverInfo", Map.of("name", "local-time-mcp", "version", "1.0.0"),
                    "capabilities", Map.of("tools", Map.of("listChanged", false))));
            case "tools/list" -> response(id, Map.of("tools", tools()));
            case "tools/call" -> response(id, callTool(request));
            default -> error(id, -32601, "method not found: " + method);
        };
    }

    private static List<Map<String, Object>> tools() {
        return List.of(
                Map.of(
                        "name", "get_current_date",
                        "title", "Current date",
                        "description", "Return the current date for a timezone.",
                        "inputSchema", timezoneSchema(),
                        "outputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of("date", Map.of("type", "string")))),
                Map.of(
                        "name", "get_current_time",
                        "title", "Current time",
                        "description", "Return the current time for a timezone.",
                        "inputSchema", timezoneSchema(),
                        "outputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of("time", Map.of("type", "string")))),
                Map.of(
                        "name", "get_machine_info",
                        "title", "Local machine information",
                        "description", "Return basic local machine information for MCP middleware verification.",
                        "inputSchema", Map.of("type", "object", "properties", Map.of(), "required", List.of()),
                        "outputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "osName", Map.of("type", "string"),
                                        "osArch", Map.of("type", "string"),
                                        "availableProcessors", Map.of("type", "integer"),
                                        "javaVersion", Map.of("type", "string")))));
    }

    private static Map<String, Object> timezoneSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("timezone", Map.of(
                        "type", "string",
                        "description", "IANA timezone, for example Asia/Shanghai")),
                "required", List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> callTool(Map<String, Object> request) {
        Map<String, Object> params = request.get("params") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        String name = String.valueOf(params.get("name"));
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        ZoneId zone = zone(arguments.get("timezone"));
        if ("get_current_date".equals(name)) {
            String date = LocalDate.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE);
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", date)),
                    "structuredContent", Map.of("date", date, "timezone", zone.getId()),
                    "isError", false);
        }
        if ("get_current_time".equals(name)) {
            String time = LocalTime.now(zone).withNano(0).format(DateTimeFormatter.ISO_LOCAL_TIME);
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", time)),
                    "structuredContent", Map.of("time", time, "timezone", zone.getId()),
                    "isError", false);
        }
        if ("get_machine_info".equals(name)) {
            Map<String, Object> info = machineInfo();
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", info.toString())),
                    "structuredContent", info,
                    "isError", false);
        }
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", "tool not found: " + name)),
                "isError", true);
    }

    private static Map<String, Object> machineInfo() {
        return Map.of(
                "osName", System.getProperty("os.name", "unknown"),
                "osArch", System.getProperty("os.arch", "unknown"),
                "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "javaVersion", System.getProperty("java.version", "unknown"));
    }

    private static ZoneId zone(Object timezone) {
        if (timezone instanceof String value && !value.isBlank()) {
            return ZoneId.of(value);
        }
        return DEFAULT_ZONE;
    }

    private static Map<String, Object> response(Object id, Map<String, Object> result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("code", code, "message", message));
    }
}
