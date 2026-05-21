package com.huawei.ascend.service.runtime.probe;

/*
 * W0 U2 promotion probe for runtime-side critical-path deps (pre-Phase-C this
 * lived in the agent-runtime module; post-ADR-0078 consolidated into agent-service).
 *
 * Imports cited APIs from each pinned dep so a successful `mvn compile`
 * proves the API exists at the version pinned by the parent POM. Per
 * docs/cross-cutting/oss-bill-of-materials.md (W0 promotes these to U2).
 *
 * Spring AI 2.0.0-M5, Temporal 1.35.0, MCP 1.0.0 GA, Tika 3.3.0 verified
 * at U2 once this probe compiles.
 */

// Spring AI 2.0.x
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

// Temporal Java SDK 1.35.0
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.client.WorkflowClient;

// MCP Java SDK 1.0.0 GA (replaces milestone 2.0.0-M2)
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

// Apache Tika 3.3.0
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.metadata.Metadata;

public final class OssApiProbe {

    private OssApiProbe() {}

    public static String probe() {
        Class<?>[] cites = new Class<?>[]{
                ChatClient.class,
                ChatModel.class,
                EmbeddingModel.class,
                VectorStore.class,
                Workflow.class,
                WorkflowInterface.class,
                WorkflowMethod.class,
                ActivityInterface.class,
                ActivityMethod.class,
                WorkflowClient.class,
                McpClient.class,
                McpSyncClient.class,
                McpSchema.class,
                AutoDetectParser.class,
                Metadata.class
        };
        // Probe label retained pre-Phase-C verbatim for back-compat (was "agent-runtime W0-U2 probe: ").
        StringBuilder sb = new StringBuilder("agent-runtime W0-U2 probe: ");
        for (Class<?> c : cites) {
            sb.append(c.getSimpleName()).append(' ');
        }
        return sb.toString();
    }

    public static int temporalGetVersionShape() {
        // ADR-03 + the pre-Phase-C `agent-runtime/temporal/ARCHITECTURE.md` sec-10.1 (post-ADR-0078 the content moved into agent-service docs) cite the
        // Workflow.getVersion(String, int, int) signature for workflow versioning.
        // Compile-time reference proves the method exists at SDK 1.35.0.
        // Runtime invocation is illegal outside a workflow context.
        if (false) {
            // unreachable; here purely to type-check the signature.
            return Workflow.getVersion("never", 0, 1);
        }
        return -1;
    }
}
