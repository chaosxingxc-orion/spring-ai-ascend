package com.bank.financial.playground;

import com.bank.financial.agent.FinancialAgentRegistry;
import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.spec.AgentDefinitionLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Playground-side resolution: delegates id→handler to {@link FinancialAgentRegistry}
 * (the single source of truth shared with the served runtime), and adds
 * playground conveniences — a file-path escape hatch and {@code --demo} scripts.
 */
public final class PlaygroundCatalog {

    private PlaygroundCatalog() {
    }

    public static AbstractFinancialAgentHandler resolve(String ref) {
        // Convenience: allow a direct YAML file path in the playground.
        Path p = Path.of(ref);
        if (Files.exists(p)) {
            return new com.bank.financial.kit.DeclarativeFinancialAgentHandler(
                    AgentDefinitionLoader.loadFile(p));
        }
        return FinancialAgentRegistry.create(ref);
    }

    /**
     * For {@code --demo}: a {toolName, argsJson} that triggers this agent's
     * sensitive/representative tool, so the playground can show the
     * human-approval pause/resume (or the suitability engine output).
     */
    public static String[] demoScript(String id) {
        return switch (id) {
            case "credit-card-servicing" -> new String[] {
                    "repay", "{\"cardId\":\"6225\",\"amount\":80000}"};
            case "loan-intake" -> new String[] {
                    "submit_application",
                    "{\"applicantName\":\"张三\",\"idNumber\":\"110101199001011234\","
                            + "\"amount\":500000,\"termMonths\":36}"};
            case "aml-screening" -> new String[] {
                    "file_sar", "{\"caseId\":\"AML-2026-001\",\"narrative\":\"临界现金存入后立即跨境转出\"}"};
            case "retail-wealth-advisor" -> new String[] {
                    "recommend_products", "{\"customerId\":\"2001\"}"};
            case "private-banking-rm" -> new String[] {
                    "recommend_products", "{\"customerId\":\"2001\"}"};
            case "deposit-advisor" -> new String[] {
                    "quote_deposit", "{\"principal\":100000,\"termMonths\":12}"};
            default -> null;
        };
    }

    public static List<String> available() {
        List<String> all = new ArrayList<>(FinancialAgentRegistry.javaIds());
        for (String y : yamlIds()) {
            if (!FinancialAgentRegistry.javaIds().contains(y)) {
                all.add(y + " (yaml)");
            }
        }
        return all;
    }

    private static List<String> yamlIds() {
        List<String> ids = new ArrayList<>();
        Path dir = Path.of("financial/src/main/resources/agents");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(f -> f.toString().endsWith(".yaml"))
                        .forEach(f -> ids.add(f.getFileName().toString().replaceFirst("\\.yaml$", "")));
            } catch (Exception ignored) {
                // best-effort listing
            }
        }
        return ids;
    }
}
