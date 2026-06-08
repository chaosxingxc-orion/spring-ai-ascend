package com.huawei.ascend.runtime.engine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.EngineCommandEventFactory;
import com.huawei.ascend.runtime.engine.EngineCommandGateway;
import com.huawei.ascend.runtime.engine.EngineCommandEvent;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.EngineInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class DefaultEngineExecutionApiTest {

    private EngineExecutionScope scope() {
        return new EngineExecutionScope("t", "u", "s", "task-1", "echo-agent");
    }

    private EngineInput input() {
        return new EngineInput("text", List.of(), Map.of());
    }

    static class RecordingGateway implements EngineCommandGateway {
        final List<EngineCommandEvent> published = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean publish(EngineCommandEvent event) {
            published.add(event);
            return accept;
        }

        @Override
        public Flux<EngineCommandEvent> commands() {
            return Flux.empty();
        }
    }

    @Test
    void enqueueExecution_publishesExecuteCommandAndReturnsSuccess() {
        RecordingGateway gateway = new RecordingGateway();
        DefaultEngineExecutionApi api = new DefaultEngineExecutionApi(new EngineCommandEventFactory(), gateway);

        EnqueueEngineStatus status = api.enqueueExecution(new EnqueueEngineExecutionRequest(scope(), input()));

        assertThat(status).isEqualTo(EnqueueEngineStatus.SUCCESS);
        assertThat(gateway.published).hasSize(1);
        assertThat(gateway.published.get(0).getCommandType()).isEqualTo("EXECUTE");
    }

    @Test
    void enqueueResume_publishesResumeCommand() {
        RecordingGateway gateway = new RecordingGateway();
        DefaultEngineExecutionApi api = new DefaultEngineExecutionApi(new EngineCommandEventFactory(), gateway);

        api.enqueueResume(new EnqueueEngineResumeRequest(scope(), input()));

        assertThat(gateway.published.get(0).getCommandType()).isEqualTo("RESUME");
    }

    @Test
    void enqueueCancel_publishesCancelCommand() {
        RecordingGateway gateway = new RecordingGateway();
        DefaultEngineExecutionApi api = new DefaultEngineExecutionApi(new EngineCommandEventFactory(), gateway);

        api.enqueueCancel(new EnqueueEngineCancelRequest(scope()));

        assertThat(gateway.published.get(0).getCommandType()).isEqualTo("CANCEL");
    }

    @Test
    void enqueue_whenGatewayRejects_returnsFailed() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.accept = false;
        DefaultEngineExecutionApi api = new DefaultEngineExecutionApi(new EngineCommandEventFactory(), gateway);

        EnqueueEngineStatus status = api.enqueueExecution(new EnqueueEngineExecutionRequest(scope(), input()));

        assertThat(status).isEqualTo(EnqueueEngineStatus.FAILED);
    }
}
