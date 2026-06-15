package com.bank.financial.playground;

import com.bank.financial.agent.FinancialAdvisorAgentConfiguration.FinancialAdvisorAgentHandler;
import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.DeclarativeFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.templates.AmlScreeningAgent;
import com.bank.financial.templates.CreditCardServicingAgent;
import com.bank.financial.templates.DepositAdvisorAgent;
import com.bank.financial.templates.LoanIntakeAgent;
import com.bank.financial.templates.PrivateBankingRmCopilotAgent;
import com.bank.financial.templates.RetailWealthAdvisorAgent;
import com.bank.financial.kit.spec.AgentDefinition;
import com.bank.financial.kit.spec.AgentDefinitionLoader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Resolves an agent id to a runnable handler for the playground. Java-defined
 * agents (the primary path) are registered here, one line each; YAML-defined
 * agents are picked up automatically. Both kinds are {@link
 * AbstractFinancialAgentHandler}, so the playground runs them through one path.
 */
public final class PlaygroundCatalog {

    /** Java agents — add yours here: {@code id -> () -> new YourHandler(ModelConnection.fromEnv())}. */
    private static final Map<String, Supplier<AbstractFinancialAgentHandler>> JAVA = new LinkedHashMap<>();

    static {
        JAVA.put("financial-advisor-agent",
                () -> new FinancialAdvisorAgentHandler(ModelConnection.fromEnv()));
        // Reference templates (the bank's "industry content" — copy & customize).
        JAVA.put(CreditCardServicingAgent.ID, () -> new CreditCardServicingAgent(ModelConnection.fromEnv()));
        JAVA.put(LoanIntakeAgent.ID, () -> new LoanIntakeAgent(ModelConnection.fromEnv()));
        JAVA.put(AmlScreeningAgent.ID, () -> new AmlScreeningAgent(ModelConnection.fromEnv()));
        JAVA.put(RetailWealthAdvisorAgent.ID, RetailWealthAdvisorAgent::create);
        JAVA.put(PrivateBankingRmCopilotAgent.ID, PrivateBankingRmCopilotAgent::create);
        JAVA.put(DepositAdvisorAgent.ID, DepositAdvisorAgent::create);
    }

    private PlaygroundCatalog() {
    }

    public static AbstractFinancialAgentHandler resolve(String ref) throws Exception {
        Supplier<AbstractFinancialAgentHandler> java = JAVA.get(ref);
        if (java != null) {
            return java.get();
        }
        AgentDefinition def = loadYaml(ref);
        if (def != null) {
            return new DeclarativeFinancialAgentHandler(def);
        }
        throw new IllegalArgumentException("找不到 agent: " + ref + "  可用: " + available());
    }

    /**
     * For {@code --demo}: a {toolName, argsJson} that triggers this agent's
     * sensitive tool, so the playground can show the human-approval pause/resume.
     * Returns null for agents with no gated tool.
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
            // Wealth: same private client on the SELF-SERVICE channel → no private funds, rmNote instead.
            case "retail-wealth-advisor" -> new String[] {
                    "recommend_products", "{\"customerId\":\"2001\"}"};
            // RM copilot: the SAME client on the RM channel → eligible private funds become visible.
            case "private-banking-rm" -> new String[] {
                    "recommend_products", "{\"customerId\":\"2001\"}"};
            // Tutorial agent: deterministic interest quote (10万 / 12月 @1.5% = 1500元).
            case "deposit-advisor" -> new String[] {
                    "quote_deposit", "{\"principal\":100000,\"termMonths\":12}"};
            default -> null;
        };
    }

    public static List<String> available() {
        List<String> all = new ArrayList<>(JAVA.keySet());
        for (String y : yamlIds()) {
            if (!all.contains(y)) {
                all.add(y + " (yaml)");
            }
        }
        return all;
    }

    private static AgentDefinition loadYaml(String ref) throws Exception {
        Path p = Path.of(ref);
        if (Files.exists(p)) {
            return AgentDefinitionLoader.loadFile(p);
        }
        String cp = "agents/" + (ref.endsWith(".yaml") ? ref : ref + ".yaml");
        InputStream in = PlaygroundCatalog.class.getClassLoader().getResourceAsStream(cp);
        if (in != null) {
            return AgentDefinitionLoader.loadStream(in);
        }
        Path src = Path.of("financial/src/main/resources/agents/" + ref + ".yaml");
        return Files.exists(src) ? AgentDefinitionLoader.loadFile(src) : null;
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
