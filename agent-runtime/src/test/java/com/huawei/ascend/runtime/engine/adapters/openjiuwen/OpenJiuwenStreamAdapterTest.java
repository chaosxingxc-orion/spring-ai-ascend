package com.huawei.ascend.runtime.engine.adapters.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenStreamAdapterTest {

    private final OpenJiuwenStreamAdapter mapper = new OpenJiuwenStreamAdapter();

    @Test
    void map_answer_toCompletedWithOutput() {
        AgentExecutionResult result = mapper.map(Map.of("result_type", "answer", "output", "hi"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
        assertThat(result.output().getContent()).isEqualTo("hi");
        assertThat(result.output().isFinalOutput()).isTrue();
    }

    @Test
    void map_error_toFailed() {
        AgentExecutionResult result = mapper.map(Map.of("result_type", "error", "output", "boom"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(result.errorCode()).isEqualTo(OpenJiuwenStreamAdapter.ERROR_CODE);
        assertThat(result.errorMessage()).isEqualTo("boom");
    }

    @Test
    void map_interrupt_toInterrupted() {
        AgentExecutionResult result = mapper.map(Map.of("result_type", "interrupt", "output", "need input"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(result.prompt()).isEqualTo("need input");
    }

    @Test
    void map_nullResult_toFailed() {
        AgentExecutionResult result = mapper.map(null);

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.FAILED);
    }
}
