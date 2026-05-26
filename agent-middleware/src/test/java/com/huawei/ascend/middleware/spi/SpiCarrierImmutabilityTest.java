package com.huawei.ascend.middleware.spi;

import com.huawei.ascend.middleware.embedding.spi.Embedding;
import com.huawei.ascend.middleware.memory.spi.KnowledgeDocument;
import com.huawei.ascend.middleware.memory.spi.MemoryCategory;
import com.huawei.ascend.middleware.memory.spi.MemoryMetadata;
import com.huawei.ascend.middleware.memory.spi.MemoryOwnership;
import com.huawei.ascend.middleware.memory.spi.MemoryQuery;
import com.huawei.ascend.middleware.model.spi.ModelFinishReason;
import com.huawei.ascend.middleware.model.spi.Message;
import com.huawei.ascend.middleware.model.spi.ModelInvocation;
import com.huawei.ascend.middleware.model.spi.ModelResponse;
import com.huawei.ascend.middleware.retrieval.spi.RetrievedDocument;
import com.huawei.ascend.middleware.retrieval.spi.RetrievalOptions;
import com.huawei.ascend.middleware.skill.spi.SkillContext;
import com.huawei.ascend.middleware.skill.spi.SkillInvocation;
import com.huawei.ascend.middleware.skill.spi.SkillResult;
import com.huawei.ascend.middleware.skill.spi.SkillSuspensionState;
import com.huawei.ascend.middleware.vector.spi.Document;
import com.huawei.ascend.middleware.vector.spi.VectorQuery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpiCarrierImmutabilityTest {

    @Test
    void modelInvocationCopiesEveryMutableComponent() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message.UserMessage("hello"));
        List<String> tools = new ArrayList<>(List.of("search"));
        Map<String, Object> parameters = new HashMap<>(Map.of("temperature", 0.2));
        Map<String, Object> hookContext = new HashMap<>(Map.of("traceId", "trace"));

        ModelInvocation invocation = new ModelInvocation(
                "tenant",
                "model",
                messages,
                tools,
                parameters,
                hookContext);

        messages.add(new Message.UserMessage("mutated"));
        tools.add("shell");
        parameters.put("temperature", 0.9);
        hookContext.put("traceId", "mutated");

        assertThat(invocation.messages()).extracting(Message::content).containsExactly("hello");
        assertThat(invocation.tools()).containsExactly("search");
        assertThat(invocation.parameters()).containsEntry("temperature", 0.2);
        assertThat(invocation.hookContext()).containsEntry("traceId", "trace");
        assertThatThrownBy(() -> invocation.tools().add("new")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> invocation.parameters().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void modelResponseAndAssistantMessageCopyToolCallLists() {
        ModelResponse.ToolCall call = new ModelResponse.ToolCall("call", "search", "{}");
        List<ModelResponse.ToolCall> toolCalls = new ArrayList<>(List.of(call));
        Map<String, Object> metadata = new HashMap<>(Map.of("provider", "openai"));

        ModelResponse response = new ModelResponse("content", toolCalls, ModelFinishReason.STOP, null, metadata);
        Message.AssistantMessage assistant = new Message.AssistantMessage("", toolCalls);

        toolCalls.add(new ModelResponse.ToolCall("call-2", "shell", "{}"));
        metadata.put("provider", "mutated");

        assertThat(response.toolCalls()).containsExactly(call);
        assertThat(response.finishReason()).isEqualTo(ModelFinishReason.STOP);
        assertThat(response.metadata()).containsEntry("provider", "openai");
        assertThat(assistant.toolCalls()).containsExactly(call);
        assertThatThrownBy(() -> response.toolCalls().add(call))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> assistant.toolCalls().add(call))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void vectorAndEmbeddingCarriersCloneArraysAndCopyMaps() {
        float[] embedding = new float[] {1.0f, 2.0f};
        Map<String, Object> metadata = new HashMap<>(Map.of("source", "doc"));
        Embedding value = new Embedding(embedding, "v1");
        Document document = new Document("doc", "content", embedding, metadata);
        RetrievedDocument retrievedDocument = new RetrievedDocument("doc", "content", embedding, metadata);

        embedding[0] = 9.0f;
        metadata.put("source", "mutated");

        assertThat(value.vector()).containsExactly(1.0f, 2.0f);
        assertThat(document.embedding()).containsExactly(1.0f, 2.0f);
        assertThat(retrievedDocument.embedding()).containsExactly(1.0f, 2.0f);
        assertThat(document.metadata()).containsEntry("source", "doc");
        assertThat(retrievedDocument.metadata()).containsEntry("source", "doc");

        float[] returned = document.embedding();
        returned[0] = 7.0f;
        assertThat(document.embedding()).containsExactly(1.0f, 2.0f);
        float[] returnedRetrieved = retrievedDocument.embedding();
        returnedRetrieved[0] = 8.0f;
        assertThat(retrievedDocument.embedding()).containsExactly(1.0f, 2.0f);
        assertThatThrownBy(() -> document.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> retrievedDocument.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void vectorQueryAndRetrievalOptionsCopyProviderHints() {
        float[] queryEmbedding = new float[] {0.1f, 0.2f};
        Map<String, Object> providerHints = new HashMap<>(Map.of("reranker", "r1"));
        VectorQuery query = new VectorQuery(null, queryEmbedding, 5, 0.5, null, "emb-v1", providerHints);
        RetrievalOptions options = new RetrievalOptions(5, 0.5, null, providerHints);

        queryEmbedding[0] = 0.9f;
        providerHints.put("reranker", "r2");

        assertThat(query.queryEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(query.providerHints()).containsEntry("reranker", "r1");
        assertThat(options.providerHints()).containsEntry("reranker", "r1");

        float[] returned = query.queryEmbedding();
        returned[0] = 0.8f;
        assertThat(query.queryEmbedding()).containsExactly(0.1f, 0.2f);
        assertThatThrownBy(() -> options.providerHints().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void memoryAndSkillCarriersCopyMaps() {
        Map<String, Object> attributes = new HashMap<>(Map.of("source", "kb"));
        Map<String, String> tags = new HashMap<>(Map.of("kind", "fact"));
        Map<String, Object> config = new HashMap<>(Map.of("timeout", 1));
        Map<String, Object> inputs = new HashMap<>(Map.of("query", "hello"));
        Map<String, Object> hookContext = new HashMap<>(Map.of("traceId", "trace"));
        Map<String, Object> outputs = new HashMap<>(Map.of("result", "ok"));
        Map<String, Object> payload = new HashMap<>(Map.of("cursor", "c1"));

        KnowledgeDocument document = new KnowledgeDocument(
                "doc",
                "kb",
                "title",
                "content",
                "paragraph",
                attributes);
        MemoryMetadata metadata = new MemoryMetadata(
                "mem",
                Instant.EPOCH,
                Instant.EPOCH,
                "agent",
                "trace",
                "emb-v1",
                MemoryOwnership.S_SIDE,
                tags);
        MemoryQuery query = new MemoryQuery(MemoryCategory.M3_SEMANTIC, tags, null, null, 10);
        SkillContext context = new SkillContext("tenant", "trace", config);
        SkillInvocation invocation = new SkillInvocation("tenant", "skill", inputs, hookContext);
        SkillResult.SkillSuccess success = new SkillResult.SkillSuccess(outputs);
        SkillSuspensionState suspension = new SkillSuspensionState("resume", payload);

        attributes.put("source", "mutated");
        tags.put("kind", "mutated");
        config.put("timeout", 2);
        inputs.put("query", "mutated");
        hookContext.put("traceId", "mutated");
        outputs.put("result", "mutated");
        payload.put("cursor", "mutated");

        assertThat(document.attributes()).containsEntry("source", "kb");
        assertThat(metadata.tags()).containsEntry("kind", "fact");
        assertThat(query.tagFilters()).containsEntry("kind", "fact");
        assertThat(context.config()).containsEntry("timeout", 1);
        assertThat(invocation.inputs()).containsEntry("query", "hello");
        assertThat(invocation.hookContext()).containsEntry("traceId", "trace");
        assertThat(success.outputs()).containsEntry("result", "ok");
        assertThat(suspension.payload()).containsEntry("cursor", "c1");
        assertThatThrownBy(() -> success.outputs().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
