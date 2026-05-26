package com.huawei.ascend.service.integration.springai;

import com.huawei.ascend.middleware.model.spi.ModelGateway;
import com.huawei.ascend.middleware.model.spi.ModelInvocation;
import com.huawei.ascend.middleware.model.spi.ModelResponse;

import org.springframework.ai.chat.model.ChatModel;

import java.util.Objects;

/**
 * Reference {@link ModelGateway} that decorates a Spring AI
 * {@link ChatModel}.
 *
 * <p>Authority: ADR-0121 + ADR-0125. Design-only shell —
 * see package-info for the L0 vs W2 boundary.
 *
 * <p>W2 implementation responsibilities:
 * <ul>
 *   <li>Map {@link ModelInvocation#messages()} into Spring AI's
 *       {@code Prompt} / {@code Message} types.</li>
 *   <li>Fire {@code HookPoint.BEFORE_LLM} via the platform's
 *       {@code HookDispatcher} before calling {@code chatModel.call(...)}.</li>
 *   <li>Convert Spring AI's {@code ChatResponse} into
 *       {@link ModelResponse}, including tool-call extraction.</li>
 *   <li>Fire {@code HookPoint.AFTER_LLM} with the converted
 *       response.</li>
 *   <li>Honor {@code ResilienceContract.resolve(tenant,
 *       "model:" + invocation.modelId())} when per-model capacity
 *       rules exist.</li>
 * </ul>
 */
public final class SpringAiChatModelGateway implements ModelGateway {

    private final ChatModel chatModel;
    private final String gatewayId;

    public SpringAiChatModelGateway(ChatModel chatModel, String gatewayId) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.gatewayId = Objects.requireNonNull(gatewayId, "gatewayId");
    }

    @Override
    public ModelResponse invoke(ModelInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        throw new UnsupportedOperationException(
                "SpringAiChatModelGateway: design-only shell at L0; "
                        + "LLM gateway implementation wires hook dispatch + Spring AI invocation");
    }

    @Override
    public String gatewayId() {
        return gatewayId;
    }

    /** Exposes the underlying Spring AI bean for diagnostic / wiring assertions. */
    public ChatModel underlyingChatModel() {
        return chatModel;
    }
}
