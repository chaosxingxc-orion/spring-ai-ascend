package com.huawei.ascend.edp.rail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.channel.ToolDataChannel;
import com.huawei.ascend.edp.channel.ToolDataKey;
import com.huawei.ascend.edp.channel.ToolDataKeyFactory;
import com.huawei.ascend.edp.config.EdpaSpringBootConfig;
import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.ScriptConstants;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MCP 工具调用 Rail。
 */
public class McpInterruptRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpInterruptRail.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(60);

    static final String DEFAULT_MCP_PRODUCTS_KEY = "mcp_products_data";
    static final String VERSATILE_QUERY_KEY = "mcp_to_versatile_information";
    static final String HISTORY_INFO_KEY = "history_info";
    static final String HISTORY_PARAMS_KEY = "history_params";

    private final EdpConfig edpConfig;
    private final ToolDataChannel toolDataChannel;
    private final Path skillsDir;
    private final EdpaSpringBootConfig springBootConfig;

    public McpInterruptRail(EdpConfig edpConfig) {
        this(edpConfig, new ToolDataChannel(), null, null);
    }

    public McpInterruptRail(EdpConfig edpConfig, ToolDataChannel toolDataChannel) {
        this(edpConfig, toolDataChannel, null, null);
    }

    public McpInterruptRail(EdpConfig edpConfig, ToolDataChannel toolDataChannel, Path skillsDir) {
        this(edpConfig, toolDataChannel, skillsDir, null);
    }

    public McpInterruptRail(EdpConfig edpConfig, ToolDataChannel toolDataChannel, Path skillsDir,
            EdpaSpringBootConfig springBootConfig) {
        this.edpConfig = edpConfig;
        this.toolDataChannel = toolDataChannel != null ? toolDataChannel : new ToolDataChannel();
        this.skillsDir = skillsDir != null ? skillsDir.toAbsolutePath().normalize() : null;
        this.springBootConfig = springBootConfig;
        setPriority(85);
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();
        if (!"call_mcp".equals(toolName)) {
            return;
        }

        LOGGER.info("McpInterruptRail: intercepting call_mcp for local script execution");
        ctx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
        Map<String, Object> result = executeMcpScript(inputs);
        inputs.setToolResult(result);
        inputs.setToolMsg(ToolMessage.builder()
                .content(toJson(result))
                .toolCallId(inputs.getToolCall() != null ? inputs.getToolCall().getId() : "call_mcp")
                .build());
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();
        if (!"call_mcp".equals(toolName)) {
            return;
        }

        LOGGER.info("McpInterruptRail: call_mcp completed, result validated");
        Map<String, Object> result = normalizeResult(inputs);
        if (result.isEmpty()) {
            LOGGER.debug("McpInterruptRail: call_mcp result is empty, skip ToolDataChannel write");
            return;
        }
        persistMcpResult(ctx, result);
        updateToolMessage(inputs, result);
    }

    void persistMcpResult(AgentCallbackContext ctx, Map<String, Object> result) {
        ToolDataKey key = ToolDataKeyFactory.fromContext(ctx, edpConfig);
        String resultKey = asString(result.get("result_key"));
        if (isBlank(resultKey)) {
            resultKey = DEFAULT_MCP_PRODUCTS_KEY;
        }

        Map<String, Object> data = removeControlFields(result);
        toolDataChannel.store(key, resultKey, data);
        if (!DEFAULT_MCP_PRODUCTS_KEY.equals(resultKey)) {
            toolDataChannel.store(key, DEFAULT_MCP_PRODUCTS_KEY, data);
        }
        LOGGER.info("McpInterruptRail: stored call_mcp result to ToolDataChannel key={}, resultKey={}, fields={}",
                key, resultKey, data.keySet());

        Object versatileQuery = result.get("versatile_query");
        if (versatileQuery instanceof String text && !text.isBlank()) {
            toolDataChannel.store(key, VERSATILE_QUERY_KEY, Map.of("query_description", text));
            result.put("versatile_query", "");
            LOGGER.info("McpInterruptRail: cached versatile_query to ToolDataChannel key={}", key);
        }

        if (result.containsKey(HISTORY_INFO_KEY)) {
            toolDataChannel.store(key, HISTORY_INFO_KEY, Map.of("value", result.get(HISTORY_INFO_KEY)));
        }
        Object historyParams = result.get(HISTORY_PARAMS_KEY);
        if (historyParams instanceof Map<?, ?> map) {
            toolDataChannel.store(key, HISTORY_PARAMS_KEY, toStringKeyMap(map));
        }
    }

    private Map<String, Object> executeMcpScript(ToolCallInputs inputs) {
        Map<String, Object> args = normalizeArgs(inputs);
        String scriptCommand = asString(args.get("script_command"));
        if (isBlank(scriptCommand)) {
            return failedResult("script_command is blank");
        }

        Map<String, Object> scriptParams = normalizeArgsObject(args.get("script_params"));
        String argumentsJson = toJson(scriptParams);
        List<String> command = buildCommand(scriptCommand);
        Path workDir = resolveWorkDir(command);

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workDir != null) {
                builder.directory(workDir.toFile());
            }
            builder.environment().put("SKILL_INPUT", argumentsJson);
            builder.environment().put("PYTHONIOENCODING", "utf-8");

            // ---- MCP SSE 配置注入 + 灰度路由 ----
            String wapGrayFlag = extractWapGrayFlag(scriptParams);
            if (springBootConfig != null && springBootConfig.getMcpsse() != null) {
                var mcpConfig = springBootConfig.getMcpsse();
                String mcpServerUrl = (wapGrayFlag != null && wapGrayFlag.startsWith("JD"))
                        ? mcpConfig.getMasterUrl() : mcpConfig.getStandbyUrl();
                if (mcpServerUrl != null) {
                    builder.environment().put("MCP_SERVER_URL", mcpServerUrl);
                }
                if (mcpConfig.getAccessToken() != null) {
                    builder.environment().put("MCP_ACCESS_TOKEN", mcpConfig.getAccessToken());
                }
                if (mcpConfig.getAppName() != null) {
                    builder.environment().put("MCP_APP_NAME", mcpConfig.getAppName());
                }
                LOGGER.info("McpInterruptRail: MCP SSE env injected, wapGrayFlag={}, serverUrl={}",
                        wapGrayFlag, mcpServerUrl);
            }
            // ---- MCP SSE 配置注入结束 ----

            LOGGER.info("McpInterruptRail: execute script command={}, workDir={}", command, workDir);

            Process process = builder.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread stdoutThread = readAsync(process.getInputStream(), stdout);
            Thread stderrThread = readAsync(process.getErrorStream(), stderr);
            boolean finished = process.waitFor(SCRIPT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return failedResult("MCP script timeout after " + SCRIPT_TIMEOUT.toSeconds() + "s");
            }
            stdoutThread.join(1000);
            stderrThread.join(1000);

            int exitCode = process.exitValue();
            LOGGER.info("McpInterruptRail: script exitCode={}, stdoutChars={}, stderr={}",
                    exitCode, stdout.length(), abbreviate(stderr.toString()));
            if (exitCode != 0) {
                return failedResult("MCP script exitCode=" + exitCode + ", stderr=" + abbreviate(stderr.toString()));
            }
            return parseScriptOutput(stdout.toString());
        } catch (Exception e) {
            LOGGER.warn("McpInterruptRail: local script execution failed: {}", e.getMessage());
            return failedResult(e.getMessage());
        }
    }

    private Thread readAsync(java.io.InputStream inputStream, StringBuilder target) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (target.length() > 0) {
                        target.append(System.lineSeparator());
                    }
                    target.append(line);
                }
            } catch (Exception e) {
                LOGGER.debug("McpInterruptRail: failed to read process stream", e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private List<String> buildCommand(String scriptCommand) {
        List<String> tokens = splitCommand(scriptCommand);
        if (tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.size() >= 2 && isPythonCommand(tokens.get(0))) {
            tokens.set(1, resolveScriptPath(tokens.get(1)).toString());
        } else {
            tokens.set(0, resolveScriptPath(tokens.get(0)).toString());
        }
        return tokens;
    }

    private Path resolveWorkDir(List<String> command) {
        if (command.isEmpty()) {
            return null;
        }
        int scriptIndex = command.size() >= 2 && isPythonCommand(command.get(0)) ? 1 : 0;
        Path scriptPath = Path.of(command.get(scriptIndex));
        return scriptPath.getParent();
    }

    private Path resolveScriptPath(String scriptPath) {
        Path path = Path.of(scriptPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (skillsDir != null) {
            Path resolved = skillsDir.resolve(scriptPath).normalize();
            if (Files.exists(resolved)) {
                return resolved;
            }
        }
        Path cwdResolved = Path.of("").toAbsolutePath().normalize().resolve(scriptPath).normalize();
        if (Files.exists(cwdResolved)) {
            return cwdResolved;
        }
        Path defaultSkillsResolved = Path.of("").toAbsolutePath().normalize()
                .resolve("../scenarios/wealth-demo/skills")
                .resolve(scriptPath)
                .normalize();
        return defaultSkillsResolved;
    }

    private boolean isPythonCommand(String command) {
        String value = command.toLowerCase();
        return value.equals("python") || value.equals("python3") || value.endsWith("python.exe");
    }

    private List<String> splitCommand(String command) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if ((ch == '\'' || ch == '"')) {
                if (inQuote && ch == quoteChar) {
                    inQuote = false;
                } else if (!inQuote) {
                    inQuote = true;
                    quoteChar = ch;
                } else {
                    current.append(ch);
                }
            } else if (Character.isWhitespace(ch) && !inQuote) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private Map<String, Object> parseScriptOutput(String stdout) {
        String json = lastJsonLine(stdout);
        if (isBlank(json)) {
            return failedResult("MCP script stdout is empty");
        }
        try {
            Map<String, Object> result = toStringKeyMap(OBJECT_MAPPER.readValue(json, Map.class));
            result.putIfAbsent("result_key", DEFAULT_MCP_PRODUCTS_KEY);
            return result;
        } catch (Exception e) {
            LOGGER.warn("McpInterruptRail: failed to parse script stdout JSON: {}", abbreviate(stdout));
            return failedResult("failed to parse MCP script stdout JSON: " + e.getMessage());
        }
    }

    private String lastJsonLine(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "";
        }
        String[] lines = stdout.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("{") && line.endsWith("}")) {
                return line;
            }
        }
        return stdout.trim();
    }

    private Map<String, Object> normalizeResult(ToolCallInputs inputs) {
        Map<String, Object> result = normalizeObject(inputs.getToolResult());
        if (!result.isEmpty()) {
            return result;
        }
        ToolMessage toolMsg = inputs.getToolMsg();
        return toolMsg != null ? normalizeObject(toolMsg.getContent()) : Map.of();
    }

    private Map<String, Object> normalizeObject(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(text);
                if (node != null && node.isObject()) {
                    return toStringKeyMap(OBJECT_MAPPER.convertValue(node, Map.class));
                }
            } catch (Exception e) {
                LOGGER.warn("McpInterruptRail: failed to parse call_mcp result JSON: {}", abbreviate(text));
            }
        }
        return Map.of();
    }

    private Map<String, Object> normalizeArgs(ToolCallInputs inputs) {
        Map<String, Object> args = normalizeArgsObject(inputs.getToolArgs());
        if (args.isEmpty() && inputs.getToolCall() != null) {
            args = normalizeArgsObject(inputs.getToolCall().getArguments());
        }
        return args;
    }

    private Map<String, Object> normalizeArgsObject(Object toolArgs) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (toolArgs instanceof Map<?, ?> map) {
            map.forEach((key, value) -> args.put(String.valueOf(key), value));
            return args;
        }
        if (toolArgs instanceof String text && !text.isBlank()) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(text);
                if (node != null && node.isObject()) {
                    node.fields().forEachRemaining(entry -> args.put(entry.getKey(), OBJECT_MAPPER.convertValue(entry.getValue(), Object.class)));
                }
            } catch (Exception e) {
                LOGGER.warn("McpInterruptRail: failed to parse tool arguments: {}", text);
            }
        }
        return args;
    }

    private Map<String, Object> removeControlFields(Map<String, Object> result) {
        Map<String, Object> data = new LinkedHashMap<>(result);
        data.remove("result_key");
        data.remove("versatile_query");
        data.remove("ui_notice");
        data.remove("response_template");
        return data;
    }

    private void updateToolMessage(ToolCallInputs inputs, Map<String, Object> result) {
        if (inputs.getToolMsg() == null) {
            return;
        }
        try {
            inputs.setToolMsg(ToolMessage.builder()
                    .content(OBJECT_MAPPER.writeValueAsString(result))
                    .toolCallId(inputs.getToolMsg().getToolCallId())
                    .build());
        } catch (Exception e) {
            LOGGER.debug("McpInterruptRail: failed to refresh tool message after control field cleanup", e);
        }
    }

    private Map<String, Object> failedResult(String message) {
        return Map.of(
                "status", "failed",
                "tool", "call_mcp",
                "mcp_error", message != null ? message : "unknown error",
                "products", List.of(),
                "total", 0,
                "versatile_query", "推荐理财产品，关键词：固收，风险等级：R2",
                "result_key", DEFAULT_MCP_PRODUCTS_KEY);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String abbreviate(String value) {
        return value != null && value.length() > 500 ? value.substring(0, 500) + "...(truncated)" : value;
    }

    /**
     * 从 script_params 中提取 wap_grayFlag。
     *
     * <p>mcp_required_params 在运行时可能是 String（Python dict repr 单引号格式）
     * 或已解析的 Map&lt;String,Object&gt;。两种类型均需处理。</p>
     */
    private String extractWapGrayFlag(Map<String, Object> scriptParams) {
        Object mcpRequired = scriptParams.get("mcp_required_params");
        if (mcpRequired instanceof String mcpRequiredStr) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("wap_grayFlag['\"]\\s*:\\s*['\"]([^'\"]+)")
                    .matcher(mcpRequiredStr);
            if (m.find()) {
                return m.group(1);
            }
        } else if (mcpRequired instanceof Map<?, ?> mcpRequiredMap) {
            Object customData = mcpRequiredMap.get("custom_data");
            if (customData instanceof Map<?, ?> cd) {
                Object inputs = cd.get("inputs");
                if (inputs instanceof Map<?, ?> in) {
                    Object flag = in.get("wap_grayFlag");
                    if (flag != null) {
                        return flag.toString();
                    }
                }
            }
        }
        return null;
    }
}
