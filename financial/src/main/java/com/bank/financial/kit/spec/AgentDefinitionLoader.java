package com.bank.financial.kit.spec;

import com.bank.financial.kit.spec.AgentDefinition.ApprovalRule;
import com.bank.financial.kit.spec.AgentDefinition.ModelSpec;
import com.bank.financial.kit.spec.AgentDefinition.ToolDef;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads {@link AgentDefinition}s from YAML. Supports {@code ${VAR}} and
 * {@code ${VAR:default}} environment interpolation (applied to the raw text
 * before parsing). Tolerant by design — sensible defaults so a half-filled file
 * still runs in the playground.
 */
public final class AgentDefinitionLoader {

    private static final Pattern ENV = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}");

    private AgentDefinitionLoader() {
    }

    public static AgentDefinition loadFile(Path yaml) {
        try {
            return loadString(Files.readString(yaml, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load agent yaml: " + yaml + " — " + e.getMessage(), e);
        }
    }

    public static AgentDefinition loadStream(InputStream in) {
        try (in) {
            return loadString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load agent yaml stream — " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static AgentDefinition loadString(String rawYaml) {
        String resolved = interpolateEnv(rawYaml);
        Object parsed = new Yaml().load(resolved);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("agent yaml root must be a mapping");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;

        String id = str(root.get("id"), required("id"));
        String description = str(root.get("description"), id);
        String prompt = str(root.get("prompt"), "You are a helpful banking assistant.");
        int maxIterations = intOr(root.get("maxIterations"), 6);

        ModelSpec model = parseModel(asMap(root.get("model")));
        RiskLevel compliance = parseCompliance(asMap(root.get("compliance")));
        List<ToolDef> tools = parseTools(asList(root.get("tools")));
        List<ApprovalRule> approvals = parseApprovals(asList(root.get("approvals")));

        return new AgentDefinition(id, description, prompt, model, compliance, maxIterations, tools, approvals);
    }

    // ── parsing helpers ──────────────────────────────────────────────────────

    private static ModelSpec parseModel(Map<String, Object> m) {
        if (m == null) {
            m = Map.of();
        }
        return new ModelSpec(
                str(m.get("provider"), "openai"),
                str(m.get("apiKey"), "sk-local-placeholder"),
                str(m.get("apiBase"), "http://localhost:4000/v1"),
                str(m.get("modelName"), "gpt-5.4-mini"),
                boolOr(m.get("sslVerify"), true));
    }

    private static RiskLevel parseCompliance(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        String level = str(m.get("level"), null);
        if (level == null || level.isBlank()) {
            return null;
        }
        return RiskLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private static List<ToolDef> parseTools(List<Object> raw) {
        List<ToolDef> out = new ArrayList<>();
        for (Object o : raw) {
            Map<String, Object> t = asMap(o);
            if (t == null) {
                continue;
            }
            Map<String, Object> http = asMap(t.get("http"));
            String method = http != null ? str(http.get("method"), "GET") : str(t.get("method"), "GET");
            String url = http != null ? str(http.get("url"), null) : str(t.get("url"), null);
            Map<String, String> headers = new LinkedHashMap<>();
            Map<String, Object> rawHeaders = http != null ? asMap(http.get("headers")) : asMap(t.get("headers"));
            if (rawHeaders != null) {
                rawHeaders.forEach((k, v) -> headers.put(k, String.valueOf(v)));
            }
            Map<String, Object> inputParams = asMap(t.get("inputSchema"));
            if (inputParams == null) {
                inputParams = asMap(t.get("inputParams"));
            }
            out.add(new ToolDef(
                    str(t.get("name"), required("tool.name")),
                    str(t.get("description"), ""),
                    method == null ? "GET" : method.toUpperCase(Locale.ROOT),
                    url,
                    headers,
                    inputParams == null ? Map.of() : inputParams));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<ApprovalRule> parseApprovals(List<Object> raw) {
        List<ApprovalRule> out = new ArrayList<>();
        for (Object o : raw) {
            Map<String, Object> a = asMap(o);
            if (a == null) {
                continue;
            }
            List<String> tools = new ArrayList<>();
            Object toolField = a.containsKey("tools") ? a.get("tools") : a.get("tool");
            if (toolField instanceof List<?> list) {
                list.forEach(x -> tools.add(String.valueOf(x)));
            } else if (toolField != null) {
                tools.add(String.valueOf(toolField));
            }
            Double amountOver = a.get("amountOver") == null ? null : dbl(a.get("amountOver"));
            out.add(new ApprovalRule(
                    tools,
                    str(a.get("message"), "该操作需要人工审批确认。"),
                    amountOver,
                    str(a.get("amountField"), "amount")));
        }
        return out;
    }

    // ── env interpolation ────────────────────────────────────────────────────

    private static String interpolateEnv(String raw) {
        Matcher m = ENV.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String var = m.group(1);
            String def = m.group(2);
            String val = System.getenv(var);
            if (val == null) {
                val = System.getProperty(var);
            }
            if (val == null) {
                val = def;
            }
            if (val == null) {
                throw new IllegalStateException(
                        "environment variable ${" + var + "} is not set and has no default");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── tiny coercion helpers ────────────────────────────────────────────────

    private static String required(String field) {
        return null; // marker for str(): null default means "throw if missing"
    }

    private static String str(Object v, String def) {
        if (v != null) {
            return String.valueOf(v);
        }
        if (def == null) {
            throw new IllegalArgumentException("missing required field");
        }
        return def;
    }

    private static int intOr(Object v, int def) {
        return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
    }

    private static boolean boolOr(Object v, boolean def) {
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v).trim());
    }

    private static double dbl(Object v) {
        return Double.parseDouble(String.valueOf(v).trim());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }
}
