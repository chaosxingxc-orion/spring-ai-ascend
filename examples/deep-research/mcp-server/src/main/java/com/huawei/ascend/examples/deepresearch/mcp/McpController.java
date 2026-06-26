package com.huawei.ascend.examples.deepresearch.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON-RPC 2.0 endpoint implementing the subset of MCP {@code 2025-06-18}
 * that the agent-runtime {@link
 * com.huawei.ascend.runtime.engine.mcp.HttpMcpProvider HttpMcpProvider} issues
 * over the streamable-http transport: {@code initialize}, the {@code
 * notifications/initialized} notify, {@code tools/list}, and {@code
 * tools/call}. Unknown methods return JSON-RPC error code {@code -32601}.
 *
 * <p>The single registered tool, {@code fetch_url}, exists so the search-agent
 * dogfood probe can observe the runtime adapter's tool-discovery and
 * tool-invocation paths end-to-end without an external MCP host. Tool inputs
 * and outputs are deliberately minimal — extending the surface is left to the
 * skill / verify agents.
 */
@RestController
@RequestMapping("/mcp")
public class McpController {
    private static final Logger LOG = LoggerFactory.getLogger(McpController.class);

    private static final String JSONRPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final FetchUrlTool fetchUrlTool;

    public McpController(FetchUrlTool fetchUrlTool) {
        this.fetchUrlTool = Objects.requireNonNull(fetchUrlTool, "fetchUrlTool");
    }

    @PostMapping
    public ResponseEntity<?> handle(@RequestBody Map<String, Object> request) {
        Object id = request.get("id");
        String method = stringValue(request.get("method"));
        Map<String, Object> params = mapValue(request.get("params"));

        if (id == null) {
            LOG.info("mcp notify received method={}", method);
            return ResponseEntity.accepted().build();
        }

        LOG.info("mcp request received method={} id={}", method, id);
        return switch (method) {
            case "initialize" -> ResponseEntity.ok(success(id, initializeResult()));
            case "tools/list" -> ResponseEntity.ok(success(id, Map.of("tools", List.of(fetchUrlTool.describe()))));
            case "tools/call" -> ResponseEntity.ok(success(id, handleToolCall(params)));
            default -> ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
        };
    }

    private Map<String, Object> handleToolCall(Map<String, Object> params) {
        String name = stringValue(params.get("name"));
        Map<String, Object> arguments = mapValue(params.get("arguments"));
        if (!fetchUrlTool.name().equals(name)) {
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Unknown tool: " + name)),
                    "isError", true);
        }
        return fetchUrlTool.call(arguments);
    }

    private static Map<String, Object> initializeResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", "deep-research-mcp-server", "version", "0.1.0"));
        return result;
    }

    private static Map<String, Object> success(Object id, Object result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", JSONRPC_VERSION);
        body.put("id", id);
        body.put("result", result);
        return body;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", JSONRPC_VERSION);
        body.put("id", id);
        body.put("error", Map.of("code", code, "message", message));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}