package com.huawei.ascend.agentsdk.example;

import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineInput;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.common.Message;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class OpenJiuwenExampleSupport {
    private OpenJiuwenExampleSupport() {
    }

    static Path resolveYamlPath(String[] args, String defaultYaml) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]).toAbsolutePath().normalize();
        }
        Path fromExampleDirectory = Path.of("openjiuwen", defaultYaml).toAbsolutePath().normalize();
        if (Files.exists(fromExampleDirectory)) {
            return fromExampleDirectory;
        }
        return Path.of("examples", "agent-sdk-example", "openjiuwen", defaultYaml)
                .toAbsolutePath()
                .normalize();
    }

    static Path proofYaml(Path yaml) throws Exception {
        String content = Files.readString(yaml)
                .replace("executeMode: openjiuwen", "executeMode: sdk-proof")
                .replace("${DEEPSEEK_API_KEY}", "sk-proof")
                .replace("- ../skills/order-analysis", "- ../../skills/order-analysis")
                .replace("- ../skills/report-writing", "- ../../skills/report-writing");
        Path proof = yaml.getParent()
                .resolve("target")
                .resolve(yaml.getFileName().toString().replace(".yaml", "-proof.yaml"));
        Files.createDirectories(proof.getParent());
        Files.writeString(proof, content);
        return proof;
    }

    static String userInput(String[] args) {
        return args.length > 1 ? args[1] : """
                请查询订单 A-10086，并计算金额 120 的折扣。
                你必须调用 queryOrder 和 calcDiscount。
                请使用 order-analysis 技能解读订单状态。
                请使用 report-writing 技能整理最终证明报告。
                最终回答必须列出工具返回的 proof 字段和使用过的技能名称。
                """;
    }

    static void printExecution(String title, Path effectiveYaml, String userInput, AgentRuntimeHandler handler) {
        System.out.println("=== " + title + " ===");
        System.out.println("agent.yaml: " + effectiveYaml);
        System.out.println("input: " + userInput);
        System.out.println("handler: " + handler.getClass().getName());
        System.out.println("agentId: " + handler.agentId());
        System.out.println();

        AgentExecutionContext context = executionContext(handler.agentId(), userInput);
        List<?> rawResults = handler.execute(context).toList();
        System.out.println("=== Raw OpenJiuwen Result ===");
        rawResults.forEach(System.out::println);
        System.out.println();

        System.out.println("=== Runtime AgentExecutionResult ===");
        handler.resultAdapter().adapt(rawResults.stream()).forEach(OpenJiuwenExampleSupport::printResult);
    }

    private static AgentExecutionContext executionContext(String agentId, String userInput) {
        EngineExecutionScope scope =
                new EngineExecutionScope("example-tenant", "example-user", "example-session", "example-task", agentId);
        EngineInput input = new EngineInput("text", List.of(Message.user(userInput)), Map.of());
        return new AgentExecutionContext(scope, input);
    }

    private static void printResult(AgentExecutionResult result) {
        System.out.println("type: " + result.type());
        if (result.output() != null) {
            System.out.println("output: " + result.output().getContent());
            System.out.println("final: " + result.output().isFinalOutput());
        }
        if (result.errorMessage() != null) {
            System.out.println("error: " + result.errorCode() + " " + result.errorMessage());
        }
        if (result.prompt() != null) {
            System.out.println("prompt: " + result.prompt());
        }
    }
}
