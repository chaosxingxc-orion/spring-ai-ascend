package com.huawei.ascend.examples.workmate.acp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.agent.RunEventBroadcaster;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcpStreamIngressTest {

    @Mock
    private AuditLedgerService auditLedgerService;

    @Mock
    private RunEventBroadcaster runEventBroadcaster;

    private AcpStreamIngress ingress;

    @BeforeEach
    void setUp() {
        ingress = new AcpStreamIngress(new AcpConverterFacade(), auditLedgerService, runEventBroadcaster);
    }

    @Test
    void ingestPersistsMergedMessageDelta() {
        UUID sessionId = UUID.randomUUID();
        when(auditLedgerService.record(any(), eq("message.delta"), any()))
                .thenReturn(new RecordedRunEvent(1, "message.delta", "{\"text\":\"hello\"}"));

        List<RecordedRunEvent> events = ingress.ingest(
                sessionId,
                List.of(
                        Map.of("sessionUpdate", "agent_message_chunk", "content", Map.of("text", "hel")),
                        Map.of("sessionUpdate", "agent_message_chunk", "content", Map.of("text", "lo"))));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventName()).isEqualTo("message.delta");

        ArgumentCaptor<RunPersistenceContext> contextCaptor = ArgumentCaptor.forClass(RunPersistenceContext.class);
        verify(auditLedgerService).record(contextCaptor.capture(), eq("message.delta"), any());
        assertThat(contextCaptor.getValue().sessionId()).isEqualTo(sessionId);
        assertThat(contextCaptor.getValue().runId()).startsWith("acp-ingest-");
        verify(runEventBroadcaster).publish(eq(sessionId), any(), any());
    }
}
