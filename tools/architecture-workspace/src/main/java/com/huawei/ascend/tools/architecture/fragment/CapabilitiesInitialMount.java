package com.huawei.ascend.tools.architecture.fragment;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wave 2 ONE-TIME mount of capabilities from docs/governance/architecture-status.yaml
 * into architecture/features/capabilities.dsl.
 * <p>
 * This is NOT a Wave-3-style continuous emitter that runs on every build.
 * After Wave 2's commit, the output file becomes hand-authored. The mount
 * preserves the existing capability inventory at migration time and gives
 * engineers a DSL surface to evolve.
 * <p>
 * Wave 6 will deprecate architecture-status.yaml#capabilities — at that point
 * architecture/features/capabilities.dsl becomes the sole authority for L1
 * capability inventory.
 * <p>
 * Invocation:
 * <pre>
 *   java -cp ... CapabilitiesInitialMount \
 *       --status docs/governance/architecture-status.yaml \
 *       --output architecture/features/capabilities.dsl
 * </pre>
 */
public final class CapabilitiesInitialMount {

    private CapabilitiesInitialMount() {
    }

    public static void main(String[] args) throws IOException {
        Path statusFile = null;
        Path outputFile = null;
        for (int i = 0; i < args.length - 1; i += 2) {
            switch (args[i]) {
                case "--status" -> statusFile = Path.of(args[i + 1]);
                case "--output" -> outputFile = Path.of(args[i + 1]);
                default -> { /* ignore */ }
            }
        }
        if (statusFile == null || outputFile == null) {
            System.err.println("Usage: --status <yaml> --output <dsl>");
            System.exit(64);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) new Yaml()
                .load(Files.newBufferedReader(statusFile, StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Map<String, Object> caps = (Map<String, Object>) root.getOrDefault("capabilities", Map.of());

        Map<String, Map<String, Object>> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> e : caps.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) m;
                sorted.put(e.getKey(), typed);
            }
        }

        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }

        try (Writer w = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            w.write("// architecture/features/capabilities.dsl\n");
            w.write("//\n");
            w.write("// Authority: ADR-0147 (Structurizr Workspace Authority).\n");
            w.write("// One-time programmatic mount from docs/governance/architecture-status.yaml#capabilities\n");
            w.write("// executed in Wave 2 of the migration. After this initial mount, the file is\n");
            w.write("// HAND-AUTHORED — engineers add new capabilities by editing here directly,\n");
            w.write("// and Wave 6 will deprecate the source YAML as the authority.\n");
            w.write("//\n");
            w.write("// The mount preserves: saa.id (from key), saa.kind (capability), saa.level\n");
            w.write("// (L1 unless explicit), saa.view (scenarios), saa.status (mapped from\n");
            w.write("// architecture-status.yaml#capabilities[].status), saa.owner (mapped from\n");
            w.write("// owner module if known, else 'architecture'), saa.sourceAdr (best-effort,\n");
            w.write("// falls back to ADR-0064 — Layer-0 governing principles — when no explicit\n");
            w.write("// l0_decision is recorded).\n");
            w.write("//\n");
            w.append("// Element count at mount: ").append(String.valueOf(sorted.size())).append("\n");
            w.append("\n");

            for (Map.Entry<String, Map<String, Object>> e : sorted.entrySet()) {
                String capKey = e.getKey();
                Map<String, Object> capData = e.getValue();
                String identifier = "cap_" + capKey.replaceAll("[^a-zA-Z0-9_]", "_");
                String saaId = "CAP-" + capKey.toUpperCase().replace("_", "-");
                String description = stringOrEmpty(capData.get("description"));
                if (description.length() > 200) {
                    description = description.substring(0, 197) + "...";
                }
                // Strip newlines and quotes from description for DSL safety.
                description = description.replace("\n", " ").replace("\"", "'");

                String statusRaw = stringOrDefault(capData.get("status"), "design_accepted");
                String mappedStatus = mapStatus(statusRaw, Boolean.TRUE.equals(capData.get("shipped")));

                String sourceAdr = resolveSourceAdr(capData);
                String owner = resolveOwner(capKey, capData);

                w.append(identifier).append(" = element \"")
                        .append(capKey).append("\" \"Capability\" \"")
                        .append(description.isEmpty() ? "L1 capability mounted from architecture-status.yaml" : description)
                        .append("\" \"SAA Capability\" {\n");
                w.append("    properties {\n");
                w.append("        \"saa.id\" \"").append(saaId).append("\"\n");
                w.append("        \"saa.kind\" \"capability\"\n");
                w.append("        \"saa.level\" \"L1\"\n");
                w.append("        \"saa.view\" \"scenarios\"\n");
                w.append("        \"saa.status\" \"").append(mappedStatus).append("\"\n");
                w.append("        \"saa.owner\" \"").append(owner).append("\"\n");
                w.append("        \"saa.sourceAdr\" \"").append(sourceAdr).append("\"\n");
                w.append("    }\n");
                w.append("}\n\n");
            }
        }

        System.out.println("Mounted " + sorted.size() + " capabilities to " + outputFile.toAbsolutePath());
    }

    private static String stringOrEmpty(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String stringOrDefault(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static String mapStatus(String statusRaw, boolean shipped) {
        // Allowed saa.status values: draft | accepted | shipped | deferred | retired
        if (shipped || "test_verified".equals(statusRaw) || "implemented_unverified".equals(statusRaw)) {
            return "shipped";
        }
        if ("design_accepted".equals(statusRaw)) {
            return "accepted";
        }
        if ("deferred".equals(statusRaw) || statusRaw.startsWith("deferred")) {
            return "deferred";
        }
        if ("retired".equals(statusRaw)) {
            return "retired";
        }
        return "draft";
    }

    private static String resolveSourceAdr(Map<String, Object> capData) {
        Object l0 = capData.get("l0_decision");
        if (l0 instanceof String s && s.startsWith("ADR-")) {
            return s;
        }
        if (l0 instanceof List<?> ls && !ls.isEmpty() && ls.get(0) instanceof String s2 && s2.startsWith("ADR-")) {
            return s2;
        }
        return "ADR-0064";
    }

    private static String resolveOwner(String capKey, Map<String, Object> capData) {
        Object impl = capData.get("implementation");
        if (impl instanceof String s) {
            if (s.contains("agent-service/")) return "agent-service";
            if (s.contains("agent-bus/")) return "agent-bus";
            if (s.contains("agent-client/")) return "agent-client";
            if (s.contains("agent-runtime/")) return "agent-runtime";
            if (s.contains("agent-middleware/")) return "agent-middleware";
            if (s.contains("agent-evolve/")) return "agent-evolve";
            if (s.contains("spring-ai-ascend-graphmemory-starter/")) return "spring-ai-ascend-graphmemory-starter";
            if (s.contains("spring-ai-ascend-dependencies/")) return "spring-ai-ascend-dependencies";
        }
        if (capKey.contains("agent_service") || capKey.contains("runtime")) return "agent-service";
        if (capKey.contains("agent_bus") || capKey.contains("bus") || capKey.contains("ingress") || capKey.contains("s2c")) return "agent-bus";
        if (capKey.contains("agent_client") || capKey.contains("client")) return "agent-client";
        if (capKey.contains("execution_engine") || capKey.contains("engine")) return "agent-runtime";
        if (capKey.contains("middleware") || capKey.contains("hook")) return "agent-middleware";
        if (capKey.contains("evolve") || capKey.contains("evolution")) return "agent-evolve";
        if (capKey.contains("graphmemory") || capKey.contains("memory")) return "spring-ai-ascend-graphmemory-starter";
        if (capKey.contains("bom") || capKey.contains("dependencies")) return "spring-ai-ascend-dependencies";
        return "architecture";
    }
}
