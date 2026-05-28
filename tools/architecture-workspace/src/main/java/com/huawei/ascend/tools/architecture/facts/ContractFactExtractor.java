package com.huawei.ascend.tools.architecture.facts;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-3 extractor: emits {@code contract_operation} + {@code schema} facts
 * by walking {@code docs/contracts/openapi-v1.yaml} and every other
 * {@code docs/contracts/*.v1.yaml} contract file.
 *
 * <p>For OpenAPI: one fact per HTTP operation, keyed by {@code <verb> <path>}.
 * Observed value carries operationId, summary, tags, requestBody schema ref,
 * response schema refs, and the auth/idempotency header requirements when
 * declared inline.
 *
 * <p>For non-OpenAPI contracts (engine-envelope, engine-hooks, s2c-callback,
 * ingress-envelope, agent-definition, model-invocation, etc.): one fact per
 * contract file at the file level. Observed value carries the contract
 * status, runtime_enforced flag, authority ADR refs, and a structural
 * inventory of the top-level keys present.
 *
 * <p>Authority: ADR-0154; Rule G-15 sub-clauses .a/.b.
 */
public final class ContractFactExtractor {

    static final String EXTRACTOR_ID = "tools/architecture-workspace#ContractFactExtractor";

    private static final List<String> HTTP_METHODS = List.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    private ContractFactExtractor() {
    }

    public static void extract(ExtractorContext ctx, Path outputFile) throws IOException {
        Path contractsDir = ctx.repoRoot().resolve("docs/contracts");
        List<Map<String, Object>> facts = new ArrayList<>();

        if (Files.isDirectory(contractsDir)) {
            Path openApi = contractsDir.resolve("openapi-v1.yaml");
            if (Files.isRegularFile(openApi)) {
                facts.addAll(extractOpenApiOperations(ctx, openApi));
            }
            try (var stream = Files.list(contractsDir)) {
                List<Path> yamls = new ArrayList<>();
                stream.forEach(p -> {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".v1.yaml") && !name.equals("openapi-v1.yaml")) {
                        yamls.add(p);
                    }
                });
                yamls.sort(Comparator.comparing(p -> p.getFileName().toString()));
                Path exemptList = ctx.repoRoot().resolve("docs/contracts/parse-exempt.txt");
                java.util.Set<String> exempt = new java.util.HashSet<>();
                if (Files.isRegularFile(exemptList)) {
                    for (String line : Files.readAllLines(exemptList, StandardCharsets.UTF_8)) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            exempt.add(trimmed);
                        }
                    }
                }
                for (Path yamlPath : yamls) {
                    Map<String, Object> fact;
                    try {
                        fact = extractContractYaml(ctx, yamlPath);
                    } catch (RuntimeException yamlEx) {
                        // Round-2 Wave B (2026-05-28 P1-3): contract parse
                        // failures MUST fail closed. The pre-2026-05-28
                        // behaviour emitted a `parse_failed: true` stub,
                        // which masked extraction failure as an
                        // authoritative fact. Active contract files now
                        // re-throw the parser exception unless the
                        // basename is listed in docs/contracts/parse-
                        // exempt.txt (documented non-parseable exclusion).
                        String fileName = yamlPath.getFileName().toString();
                        if (!exempt.contains(fileName)) {
                            throw new IOException(
                                    "ContractFactExtractor: " + fileName + " failed YAML parse; "
                                            + "fix the contract OR add the basename to "
                                            + "docs/contracts/parse-exempt.txt -- Rule G-15.b / P1-3",
                                    yamlEx);
                        }
                        Map<String, Object> observed = new LinkedHashMap<>();
                        observed.put("file", fileName);
                        observed.put("parse_failed", true);
                        observed.put("parse_error_class", yamlEx.getClass().getSimpleName());
                        observed.put("exempt", true);
                        String slug = fileName.substring(0, fileName.length() - ".v1.yaml".length()).toLowerCase();
                        fact = FactWriter.entry(
                                "contract-yaml/" + slug,
                                "schema",
                                "contract",
                                "docs/contracts/" + fileName,
                                slug,
                                EXTRACTOR_ID,
                                ctx.extractorVersion(),
                                ctx.repoCommit(),
                                observed);
                    }
                    if (fact != null) {
                        facts.add(fact);
                    }
                }
            }
        }

        FactWriter.write(outputFile, EXTRACTOR_ID, ctx.extractorVersion(), ctx.repoCommit(), facts);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractOpenApiOperations(ExtractorContext ctx, Path openApi)
            throws IOException {
        List<Map<String, Object>> facts = new ArrayList<>();
        try (var in = Files.newBufferedReader(openApi, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map<?, ?> raw)) {
                return facts;
            }
            Object pathsObj = raw.get("paths");
            if (!(pathsObj instanceof Map<?, ?> paths)) {
                return facts;
            }
            // Sort paths for determinism.
            List<String> pathKeys = new ArrayList<>();
            paths.keySet().forEach(k -> pathKeys.add(String.valueOf(k)));
            pathKeys.sort(Comparator.naturalOrder());
            for (String pathKey : pathKeys) {
                Object pathObj = paths.get(pathKey);
                if (!(pathObj instanceof Map<?, ?> pathItem)) {
                    continue;
                }
                for (String method : HTTP_METHODS) {
                    Object opObj = pathItem.get(method);
                    if (!(opObj instanceof Map<?, ?> op)) {
                        continue;
                    }
                    Map<String, Object> observed = new LinkedHashMap<>();
                    observed.put("http_method", method.toUpperCase());
                    observed.put("path", pathKey);
                    if (op.containsKey("operationId")) {
                        observed.put("operation_id", op.get("operationId"));
                    }
                    if (op.containsKey("summary")) {
                        observed.put("summary", op.get("summary"));
                    }
                    if (op.containsKey("tags")) {
                        observed.put("tags", op.get("tags"));
                    }
                    if (op.containsKey("requestBody")) {
                        observed.put("request_body_present", true);
                    }
                    if (op.containsKey("responses") && op.get("responses") instanceof Map<?, ?> responses) {
                        List<String> codes = new ArrayList<>();
                        responses.keySet().forEach(k -> codes.add(String.valueOf(k)));
                        codes.sort(Comparator.naturalOrder());
                        observed.put("response_status_codes", codes);
                    }
                    Object opIdRaw = op.get("operationId");
                    String opId = opIdRaw != null
                            ? String.valueOf(opIdRaw).toLowerCase()
                            : (method + "-" + pathKey).replaceAll("[^a-z0-9/-]", "-");
                    facts.add(FactWriter.entry(
                            "contract-op/" + opId,
                            "contract_operation",
                            "contract",
                            "docs/contracts/openapi-v1.yaml",
                            method.toUpperCase() + " " + pathKey,
                            EXTRACTOR_ID,
                            ctx.extractorVersion(),
                            ctx.repoCommit(),
                            observed));
                }
            }
        }
        return facts;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractContractYaml(ExtractorContext ctx, Path yamlPath) throws IOException {
        String fileName = yamlPath.getFileName().toString();
        try (var in = Files.newBufferedReader(yamlPath, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map<?, ?> raw)) {
                return null;
            }
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("file", fileName);
            for (String key : List.of("title", "status", "runtime_enforced", "authority",
                    "source_adr", "schema_version", "version", "design_only")) {
                if (raw.containsKey(key)) {
                    observed.put(key, raw.get(key));
                }
            }
            // Surface the top-level structural keys present (without their bodies).
            List<String> structural = new ArrayList<>();
            raw.keySet().forEach(k -> structural.add(String.valueOf(k)));
            structural.sort(Comparator.naturalOrder());
            observed.put("top_level_keys", structural);

            String slug = fileName.substring(0, fileName.length() - ".v1.yaml".length()).toLowerCase();
            return FactWriter.entry(
                    "contract-yaml/" + slug,
                    "schema",
                    "contract",
                    "docs/contracts/" + fileName,
                    slug,
                    EXTRACTOR_ID,
                    ctx.extractorVersion(),
                    ctx.repoCommit(),
                    observed);
        }
    }
}
