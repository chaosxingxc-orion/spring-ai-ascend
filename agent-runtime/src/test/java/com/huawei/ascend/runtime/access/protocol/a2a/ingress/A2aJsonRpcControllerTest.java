package com.huawei.ascend.runtime.access.protocol.a2a.ingress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputHandle;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputRegistry;
import com.huawei.ascend.runtime.access.protocol.a2a.jsonrpc.A2aJsonRpcStreamExchange;
import com.huawei.ascend.runtime.access.protocol.a2a.jsonrpc.A2aJsonRpcHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class A2aJsonRpcControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void messageStreamAcceptsSdkEndpointWithoutTrailingSlash() throws Exception {
        A2aJsonRpcHandler handler = mock(A2aJsonRpcHandler.class);
        String body = objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", "request-stream",
                "method", "message/stream",
                "params", Map.of(
                        "message", Map.of(
                                "role", "user",
                                "kind", "message",
                                "contextId", "session-1",
                                "messageId", UUID.randomUUID().toString(),
                                "metadata", Map.of(
                                        "userId", "user-1",
                                        "sessionId", "session-1"),
                                "parts", List.of(Map.of(
                                        "kind", "text",
                                        "text", "ping"))))));
        when(handler.openStream(body)).thenReturn(new A2aJsonRpcStreamExchange(
                "request-stream",
                Map.of("accepted", Boolean.TRUE),
                new A2aOutputHandle("tenant-1", "session-1", "task-1")));
        when(handler.toJson(org.mockito.ArgumentMatchers.any())).thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"request-stream\"}");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new A2aJsonRpcController(handler, new A2aOutputRegistry()))
                .build();

        mockMvc.perform(post("/a2a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(request().asyncStarted());
    }
}
