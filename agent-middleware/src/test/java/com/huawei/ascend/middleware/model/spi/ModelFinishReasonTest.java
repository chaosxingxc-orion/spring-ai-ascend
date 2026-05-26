package com.huawei.ascend.middleware.model.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelFinishReasonTest {

    @Test
    void finishReasonParsesOnlyCanonicalWireValues() {
        assertThat(ModelFinishReason.fromWireValue("stop")).isEqualTo(ModelFinishReason.STOP);
        assertThat(ModelFinishReason.fromWireValue("length")).isEqualTo(ModelFinishReason.LENGTH);
        assertThat(ModelFinishReason.fromWireValue("tool_calls")).isEqualTo(ModelFinishReason.TOOL_CALLS);
        assertThat(ModelFinishReason.fromWireValue("content_filter")).isEqualTo(ModelFinishReason.CONTENT_FILTER);
        assertThat(ModelFinishReason.fromWireValue("other")).isEqualTo(ModelFinishReason.OTHER);
    }

    @Test
    void finishReasonRejectsUnknownProviderStrings() {
        assertThatThrownBy(() -> ModelFinishReason.fromWireValue("function_call"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finishReason");
        assertThatThrownBy(() -> ModelFinishReason.fromWireValue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("finishReason");
    }
}
