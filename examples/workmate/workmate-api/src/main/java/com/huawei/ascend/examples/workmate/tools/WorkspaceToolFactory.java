package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.approval.ToolRiskPolicy;
import com.huawei.ascend.examples.workmate.filehistory.FileHistoryService;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactContract;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceToolFactory {

    private static final int MAX_READ_BYTES = 512 * 1024;

    private final WorkspaceShellExecutor shellExecutor;
    private final FileHistoryService fileHistoryService;

    public WorkspaceToolFactory(WorkspaceShellExecutor shellExecutor, FileHistoryService fileHistoryService) {
        this.shellExecutor = shellExecutor;
        this.fileHistoryService = fileHistoryService;
    }

    public WorkspaceToolSet create(
            Path workspaceRoot, String agentTag, ToolExecutionContext executionContext, UUID sessionId) {
        return create(workspaceRoot, agentTag, executionContext, sessionId, PermissionMode.CRAFT);
    }

    public WorkspaceToolSet create(
            Path workspaceRoot,
            String agentTag,
            ToolExecutionContext executionContext,
            UUID sessionId,
            PermissionMode mode) {
        PermissionMode effective = mode != null ? mode : PermissionMode.CRAFT;
        String readId = WorkmateToolIds.read(sessionId);
        String writeId = WorkmateToolIds.write(sessionId);
        String bashId = WorkmateToolIds.bash(sessionId);
        Tool read = new ReadTool(workspaceRoot, readId);
        java.util.ArrayList<Tool> tools = new java.util.ArrayList<>();
        tools.add(read);
        if (effective.allowsWrite()) {
            tools.add(new WriteTool(workspaceRoot, writeId, fileHistoryService, executionContext));
        }
        if (effective.allowsBash()) {
            tools.add(new BashTool(workspaceRoot, executionContext, shellExecutor, bashId));
        }
        for (Tool tool : tools) {
            safeRemove(tool.getCard().getId(), agentTag);
            Runner.resourceMgr().addTool(tool, agentTag);
        }
        return new WorkspaceToolSet(List.copyOf(tools), agentTag, sessionId);
    }

    public void unregister(WorkspaceToolSet toolSet) {
        for (Tool tool : toolSet.tools()) {
            safeRemove(tool.getCard().getId(), toolSet.agentTag());
        }
    }

    private static void safeRemove(String toolId, String agentTag) {
        try {
            Runner.resourceMgr().removeTool(toolId, agentTag, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // first registration
        }
    }

    public record WorkspaceToolSet(List<Tool> tools, String agentTag, UUID sessionId) {
    }

    private static final class ReadTool extends LocalFunction {

        ReadTool(Path workspaceRoot, String toolId) {
            super(buildCard(toolId, "Read a UTF-8 text file under the session workspace (relative path).",
                            Map.of("path", stringProp("Relative file path, e.g. hello.md"))),
                    inputs -> read(workspaceRoot, toolId, inputs));
        }

        private static Map<String, Object> read(Path workspaceRoot, String toolId, Map<String, Object> inputs) {
            Map<String, Object> blocked = policyBlocked(toolId, inputs);
            if (blocked != null) {
                return blocked;
            }
            try {
                Path file = WorkspacePathGuard.resolve(workspaceRoot, asString(inputs.get("path")));
                String content = WorkspacePathGuard.readText(file, MAX_READ_BYTES);
                return WorkspacePathGuard.success(Map.of("path", file.getFileName().toString(), "content", content));
            } catch (Exception ex) {
                return WorkspacePathGuard.failure(ex.getMessage());
            }
        }
    }

    private static final class WriteTool extends LocalFunction {

        WriteTool(
                Path workspaceRoot,
                String toolId,
                FileHistoryService fileHistoryService,
                ToolExecutionContext executionContext) {
            super(buildCard(toolId, "Write UTF-8 text to a file under the session workspace (creates parent dirs).",
                            Map.of(
                                    "path", stringProp("Relative file path"),
                                    "content", stringProp("File content"))),
                    inputs -> write(workspaceRoot, toolId, fileHistoryService, executionContext, inputs));
        }

        private static Map<String, Object> write(
                Path workspaceRoot,
                String toolId,
                FileHistoryService fileHistoryService,
                ToolExecutionContext executionContext,
                Map<String, Object> inputs) {
            Map<String, Object> blocked = policyBlocked(toolId, inputs);
            if (blocked != null) {
                return blocked;
            }
            try {
                String relativePath = asString(inputs.get("path"));
                Optional<String> violation = OfficeArtifactContract.validateAgentWritePath(relativePath);
                if (violation.isPresent()) {
                    return WorkspacePathGuard.failure(violation.get());
                }
                if (fileHistoryService != null && executionContext != null) {
                    fileHistoryService.recordBeforeWrite(
                            executionContext.sessionId(),
                            workspaceRoot,
                            relativePath,
                            executionContext.taskId());
                }
                Path file = WorkspacePathGuard.resolve(workspaceRoot, relativePath);
                Files.createDirectories(file.getParent() != null ? file.getParent() : workspaceRoot);
                Files.writeString(file, asString(inputs.get("content")));
                return WorkspacePathGuard.success(Map.of("path", file.getFileName().toString(), "bytes", Files.size(file)));
            } catch (Exception ex) {
                return WorkspacePathGuard.failure(ex.getMessage());
            }
        }
    }

    static final class BashTool extends LocalFunction {

        BashTool(Path workspaceRoot, ToolExecutionContext ctx, WorkspaceShellExecutor shellExecutor, String toolId) {
            super(buildCard(toolId, "Run a shell command with cwd = session workspace. Use for listing files or simple scripts.",
                            Map.of("command", stringProp("Shell command"))),
                    inputs -> bash(workspaceRoot, ctx, shellExecutor, toolId, inputs));
        }

        static Map<String, Object> bash(
                Path workspaceRoot,
                ToolExecutionContext ctx,
                WorkspaceShellExecutor shellExecutor,
                String toolName,
                Map<String, Object> inputs) {
            String command = asString(inputs.get("command"));
            if (command.isBlank()) {
                return WorkspacePathGuard.failure("command must not be blank");
            }
            Map<String, Object> blocked = policyBlocked(toolName, inputs);
            if (blocked != null) {
                return blocked;
            }
            ApprovalGate gate = ctx.approvalGate();
            if (gate != null) {
                ApprovalGate.Verdict verdict = gate.await(ctx.sessionId(), ctx.taskId(), toolName, inputs);
                if (verdict == ApprovalGate.Verdict.DENIED || verdict == ApprovalGate.Verdict.TIMED_OUT) {
                    ToolRiskPolicy.RiskAssessment risk = ToolRiskPolicy.assess(toolName, inputs);
                    String message = risk.policyBlocked()
                            ? "Blocked by security policy: " + risk.summary()
                            : "User denied tool execution: " + risk.summary();
                    return WorkspacePathGuard.failure(message);
                }
            }
            try {
                return WorkspacePathGuard.success(shellExecutor.run(workspaceRoot, command));
            } catch (Exception ex) {
                return WorkspacePathGuard.failure(ex.getMessage());
            }
        }
    }

    private static ToolCard buildCard(String id, String description, Map<String, Object> properties) {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.copyOf(properties.keySet()));
        return ToolCard.builder()
                .id(id)
                .name(id)
                .description(description)
                .inputParams(inputParams)
                .build();
    }

    private static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> policyBlocked(String toolId, Map<String, Object> inputs) {
        ToolRiskPolicy.RiskAssessment risk = ToolRiskPolicy.assess(toolId, inputs);
        if (risk.policyBlocked()) {
            return WorkspacePathGuard.failure("Blocked by security policy: " + risk.summary());
        }
        return null;
    }
}
