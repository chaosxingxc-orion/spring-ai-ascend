package com.huawei.ascend.middleware.memory.spi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationMemoryCarrierImmutabilityTest {

    @Test
    void conversationTurnRejectsNullRequiredFields() {
        assertThatThrownBy(() -> new ConversationTurn(null, "hello", Instant.EPOCH, 0, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("role");
        assertThatThrownBy(() -> new ConversationTurn(ConversationRole.USER, null, Instant.EPOCH, 0, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("content");
        assertThatThrownBy(() -> new ConversationTurn(ConversationRole.USER, "hello", null, 0, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("observedAt");
        assertThatThrownBy(() -> new ConversationTurn(ConversationRole.USER, "hello", Instant.EPOCH, 0, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void conversationTurnRejectsNegativeTokenCount() {
        assertThatThrownBy(() -> new ConversationTurn(ConversationRole.USER, "hello", Instant.EPOCH, -1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenCount");
    }

    @Test
    void conversationTurnCopiesMetadataAndAcceptsZeroTokenCount() {
        Map<String, Object> metadata = new HashMap<>(Map.of("source", "chat"));

        ConversationTurn turn = new ConversationTurn(
                ConversationRole.USER,
                "hello",
                Instant.EPOCH,
                0,
                metadata);

        metadata.put("source", "mutated");

        assertThat(turn.role()).isEqualTo(ConversationRole.USER);
        assertThat(turn.content()).isEqualTo("hello");
        assertThat(turn.observedAt()).isEqualTo(Instant.EPOCH);
        assertThat(turn.tokenCount()).isZero();
        assertThat(turn.metadata()).containsEntry("source", "chat");
        assertThatThrownBy(() -> turn.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void conversationWindowCopiesTurnsAndMetadata() {
        ConversationTurn turn = new ConversationTurn(
                ConversationRole.ASSISTANT,
                "answer",
                Instant.EPOCH,
                1,
                Map.of());
        List<ConversationTurn> turns = new ArrayList<>(List.of(turn));
        Map<String, Object> metadata = new HashMap<>(Map.of("summaryVersion", 1));

        ConversationWindow window = new ConversationWindow(turns, metadata);

        turns.add(new ConversationTurn(ConversationRole.USER, "mutated", Instant.EPOCH, 1, Map.of()));
        metadata.put("summaryVersion", 2);

        assertThat(window.turns()).containsExactly(turn);
        assertThat(window.metadata()).containsEntry("summaryVersion", 1);
        assertThatThrownBy(() -> window.turns().add(turn))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> window.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
