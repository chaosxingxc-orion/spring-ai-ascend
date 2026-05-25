package com.huawei.ascend.service.agent.spi;

import com.huawei.ascend.engine.planner.spi.PlannerRef;
import com.huawei.ascend.middleware.memory.spi.MemoryCategory;
import com.huawei.ascend.middleware.memory.spi.MemoryRef;
import com.huawei.ascend.middleware.model.spi.ModelResponse;
import com.huawei.ascend.middleware.skill.spi.SkillRef;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSpiCarrierImmutabilityTest {

    @Test
    void agentDefinitionCopiesBindingsAndMetadata() {
        Set<SkillRef> tools = new HashSet<>(Set.of(new SkillRef("search")));
        Map<MemoryCategory, MemoryRef> memories = new HashMap<>(Map.of(
                MemoryCategory.M3_SEMANTIC,
                new MemoryRef("semantic", MemoryCategory.M3_SEMANTIC)));
        Map<String, Object> metadata = new HashMap<>(Map.of("owner", "team"));

        AgentDefinition definition = new AgentDefinition(
                "agent",
                "tenant",
                "Agent",
                "description",
                new ModelRef("model"),
                tools,
                memories,
                Optional.of(new PlannerRef("planner")),
                "system",
                SafetyPolicy.permissive(),
                metadata);

        tools.add(new SkillRef("shell"));
        memories.put(MemoryCategory.M5_KNOWLEDGE, new MemoryRef("kb", MemoryCategory.M5_KNOWLEDGE));
        metadata.put("owner", "mutated");

        assertThat(definition.toolBindings()).extracting(SkillRef::skillKey).containsExactly("search");
        assertThat(definition.memoryBindings()).containsOnlyKeys(MemoryCategory.M3_SEMANTIC);
        assertThat(definition.metadata()).containsEntry("owner", "team");
        assertThatThrownBy(() -> definition.toolBindings().add(new SkillRef("new")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void agentInvocationValidatesIdentityAndCopiesContext() {
        Map<String, Object> context = new HashMap<>(Map.of("traceId", "trace"));

        AgentInvocation invocation = new AgentInvocation(
                "tenant",
                "agent",
                "hello",
                Optional.of("conversation"),
                context);

        context.put("traceId", "mutated");

        assertThat(invocation.context()).containsEntry("traceId", "trace");
        assertThatThrownBy(() -> invocation.context().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AgentInvocation(
                " ",
                "agent",
                "hello",
                Optional.empty(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new AgentInvocation(
                "tenant",
                " ",
                "hello",
                Optional.empty(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    void agentResponseCopiesToolCalls() {
        ModelResponse.ToolCall call = new ModelResponse.ToolCall("call", "search", "{}");
        List<ModelResponse.ToolCall> toolCalls = new ArrayList<>(List.of(call));

        AgentResponse response = new AgentResponse(
                "done",
                toolCalls,
                null,
                "trace",
                Optional.empty());

        toolCalls.add(new ModelResponse.ToolCall("call-2", "shell", "{}"));

        assertThat(response.toolCalls()).containsExactly(call);
        assertThatThrownBy(() -> response.toolCalls().add(call))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void safetyPoliciesCopySets() {
        Set<String> pii = new HashSet<>(Set.of("EMAIL"));
        Set<String> deniedSkills = new HashSet<>(Set.of("shell"));
        Set<String> deniedMemories = new HashSet<>(Set.of("m1"));

        OutputContentPolicy outputPolicy = new OutputContentPolicy(pii, 100);
        SafetyPolicy safetyPolicy = new SafetyPolicy(
                PlaceholderPreservationPolicy.PRESERVE,
                deniedSkills,
                deniedMemories,
                outputPolicy);

        pii.add("PHONE");
        deniedSkills.add("network");
        deniedMemories.add("m2");

        assertThat(outputPolicy.redactPiiCategories()).containsExactly("EMAIL");
        assertThat(safetyPolicy.deniedSkillKeys()).containsExactly("shell");
        assertThat(safetyPolicy.deniedMemoryRefs()).containsExactly("m1");
        assertThatThrownBy(() -> safetyPolicy.deniedSkillKeys().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
