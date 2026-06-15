package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.A2AErrorCodes;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;

class A2aJsonRpcControllerTest {

    @Test
    void streamingResponseDataIsJsonRpcEventReadableByA2aSdkClient() throws Exception {
        A2aJsonRpcController controller = new A2aJsonRpcController(new SingleEventRequestHandler(), new RuntimeAccessProperties());
        String body = """
                {"jsonrpc":"2.0","id":"request-1","method":"SendStreamingMessage","params":{"message":{"role":"ROLE_USER","parts":[{"text":"ping"}],"messageId":"message-1"}}}
                """;
        A2ARequest<?> request = JSONRPCUtils.parseRequestBody(body, null);

        StepVerifier.create(controller.handleStream(request, null))
                .assertNext(event -> assertThat(sdkParsedPayload(event)).isEqualTo(StreamResponse.PayloadCase.MESSAGE))
                .verifyComplete();
    }

    @Test
    void blockingHandlerDispatchesPushNotificationConfigRequests() throws Exception {
        RecordingPushRequestHandler requestHandler = new RecordingPushRequestHandler();
        A2aJsonRpcController controller = new A2aJsonRpcController(requestHandler, new RuntimeAccessProperties());
        TaskPushNotificationConfig config = new TaskPushNotificationConfig(
                "push-1", "task-1", "http://localhost:19090/a2a/push", "token-1", null, null);

        controller.handleBlocking(new CreateTaskPushNotificationConfigRequest("request-1", config), null);
        controller.handleBlocking(new GetTaskPushNotificationConfigRequest(
                "request-2", new GetTaskPushNotificationConfigParams("task-1", "push-1")), null);
        controller.handleBlocking(new ListTaskPushNotificationConfigsRequest(
                "request-3", new ListTaskPushNotificationConfigsParams("task-1")), null);
        controller.handleBlocking(new DeleteTaskPushNotificationConfigRequest(
                "request-4", new DeleteTaskPushNotificationConfigParams("task-1", "push-1")), null);

        assertThat(requestHandler.lastCreated.get()).isEqualTo(config);
        assertThat(requestHandler.lastGet.get()).isEqualTo(new GetTaskPushNotificationConfigParams("task-1", "push-1"));
        assertThat(requestHandler.lastList.get()).isEqualTo(new ListTaskPushNotificationConfigsParams("task-1"));
        assertThat(requestHandler.lastDelete.get()).isEqualTo(new DeleteTaskPushNotificationConfigParams("task-1", "push-1"));
    }

    @Test
    void defaultPushNotificationSenderUsesA2aSdkBaseSender() {
        RuntimeAutoConfiguration configuration = new RuntimeAutoConfiguration();

        PushNotificationSender sender = configuration.a2aPushSender(new InMemoryPushNotificationConfigStore());

        assertThat(sender).isInstanceOf(BasePushNotificationSender.class);
    }

    /** A malformed request body must surface a JSON-RPC parse error, never a silent empty {}. */
    @Test
    void malformedRequestBodyReturnsJsonRpcParseErrorNotEmptyObject() throws Exception {
        A2aJsonRpcController controller =
                new A2aJsonRpcController(new SingleEventRequestHandler(), new RuntimeAccessProperties());

        Object response = controller.handle("{ this is not valid json ", null);

        String json = (String) ((ResponseEntity<?>) response).getBody();
        assertThat(json).isNotEqualTo("{}");
        JsonObject error = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("error");
        assertThat(error).as("response must carry a JSON-RPC error object").isNotNull();
        assertThat(error.get("code").getAsInt()).isEqualTo(A2AErrorCodes.JSON_PARSE.code());
        assertThat(error.get("message").getAsString()).isNotBlank();
    }

    /** An unknown JSON-RPC method must surface METHOD_NOT_FOUND, not a silent empty {}. */
    @Test
    void unknownMethodReturnsMethodNotFoundError() throws Exception {
        A2aJsonRpcController controller =
                new A2aJsonRpcController(new SingleEventRequestHandler(), new RuntimeAccessProperties());
        String body = """
                {"jsonrpc":"2.0","id":"req-unknown","method":"NoSuchMethod","params":{}}
                """;

        Object response = controller.handle(body, null);

        String json = (String) ((ResponseEntity<?>) response).getBody();
        assertThat(json).isNotEqualTo("{}");
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.getAsJsonObject("error").get("code").getAsInt())
                .isEqualTo(A2AErrorCodes.METHOD_NOT_FOUND.code());
        assertThat(obj.get("id").getAsString()).as("request id must be echoed back").isEqualTo("req-unknown");
    }

    @Test
    void handlerA2aErrorAnswersJsonRpcErrorEchoingRequestId() {
        A2aJsonRpcController controller = new A2aJsonRpcController(new FailingRequestHandler(), new RuntimeAccessProperties());

        JsonNode root = blockingResponseJson(controller.handle("""
                {"jsonrpc":"2.0","id":"request-9","method":"GetTask","params":{"id":"missing-task"}}
                """, null));

        assertThat(root.path("id").asText()).isEqualTo("request-9");
        assertThat(root.path("error").path("code").asInt()).isEqualTo(new TaskNotFoundError().getCode());
    }

    @Test
    void parsedButUndispatchedMethodAnswersMethodNotFound() {
        A2aJsonRpcController controller = new A2aJsonRpcController(new SingleEventRequestHandler(), new RuntimeAccessProperties());

        JsonNode root = blockingResponseJson(controller.handle("""
                {"jsonrpc":"2.0","id":"request-10","method":"GetExtendedAgentCard","params":{}}
                """, null));

        assertThat(root.path("error").path("code").asInt()).isEqualTo(new MethodNotFoundError().getCode());
        assertThat(root.path("id").asText()).isEqualTo("request-10");
    }

    @Test
    void tenantHeaderFlowsIntoServerCallContext() {
        ListingRequestHandler handler = new ListingRequestHandler();
        A2aJsonRpcController controller = new A2aJsonRpcController(handler, new RuntimeAccessProperties());

        controller.handle("""
                {"jsonrpc":"2.0","id":"list-2","method":"ListTasks","params":{}}
                """, "  bank-7  ");

        assertThat(handler.lastContext.get().getState())
                .containsEntry(com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor.TENANT_STATE_KEY, "bank-7");
    }

    @Test
    void missingTenantHeaderFallsBackToConfiguredDefault() {
        ListingRequestHandler handler = new ListingRequestHandler();
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.setDefaultTenantId("configured-tenant");
        A2aJsonRpcController controller = new A2aJsonRpcController(handler, access);

        controller.handle("""
                {"jsonrpc":"2.0","id":"list-3","method":"ListTasks","params":{}}
                """, null);

        assertThat(handler.lastContext.get().getState())
                .containsEntry(com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor.TENANT_STATE_KEY, "configured-tenant");
    }

    @Test
    void listTasksDispatchesToHandler() {
        ListingRequestHandler handler = new ListingRequestHandler();
        A2aJsonRpcController controller = new A2aJsonRpcController(handler, new RuntimeAccessProperties());

        JsonNode root = blockingResponseJson(controller.handle("""
                {"jsonrpc":"2.0","id":"list-1","method":"ListTasks","params":{}}
                """, null));

        assertThat(handler.listed.get()).isTrue();
        assertThat(root.path("id").asText()).isEqualTo("list-1");
        assertThat(root.has("error")).as("ListTasks must not be answered with an error: %s", root).isFalse();
    }

    @Test
    void getTaskReturnsProtoNakedTaskReadableByA2aSdkClient() throws Exception {
        A2aJsonRpcController controller =
                new A2aJsonRpcController(new TaskLookupRequestHandler(), new RuntimeAccessProperties());

        Object response = controller.handle("""
                {"jsonrpc":"2.0","id":"get-1","method":"GetTask","params":{"id":"task-1"}}
                """, null);

        String json = (String) ((ResponseEntity<?>) response).getBody();
        JsonNode root = readTree(json);
        assertThat(root.path("id").asText()).isEqualTo("get-1");
        assertThat(root.path("result").path("id").asText()).isEqualTo("task-1");
        assertThat(root.path("result").has("task"))
                .as("GetTask returns proto Task directly; only SendMessage/stream payloads use result.task")
                .isFalse();

        GetTaskResponse parsed = (GetTaskResponse) JSONRPCUtils.parseResponseBody(json, "GetTask");
        assertThat(parsed.getResult().id()).isEqualTo("task-1");
    }

    @Test
    void cancelTaskReturnsProtoNakedTaskReadableByA2aSdkClient() throws Exception {
        A2aJsonRpcController controller =
                new A2aJsonRpcController(new TaskLookupRequestHandler(), new RuntimeAccessProperties());

        Object response = controller.handle("""
                {"jsonrpc":"2.0","id":"cancel-1","method":"CancelTask","params":{"id":"task-1"}}
                """, null);

        String json = (String) ((ResponseEntity<?>) response).getBody();
        JsonNode root = readTree(json);
        assertThat(root.path("id").asText()).isEqualTo("cancel-1");
        assertThat(root.path("result").path("id").asText()).isEqualTo("task-1");
        assertThat(root.path("result").has("task"))
                .as("CancelTask returns proto Task directly; only SendMessage/stream payloads use result.task")
                .isFalse();

        CancelTaskResponse parsed = (CancelTaskResponse) JSONRPCUtils.parseResponseBody(json, "CancelTask");
        assertThat(parsed.getResult().id()).isEqualTo("task-1");
    }

    @Test
    void sendMessageBlockingKeepsProtoPayloadWrapperReadableByA2aSdkClient() throws Exception {
        A2aJsonRpcController controller =
                new A2aJsonRpcController(new BlockingMessageRequestHandler(), new RuntimeAccessProperties());

        Object response = controller.handle("""
                {"jsonrpc":"2.0","id":"send-1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","parts":[{"text":"ping"}],"messageId":"message-1"}}}
                """, null);

        String json = (String) ((ResponseEntity<?>) response).getBody();
        JsonNode root = readTree(json);
        assertThat(root.path("id").asText()).isEqualTo("send-1");
        assertThat(root.path("result").path("message").path("messageId").asText()).isEqualTo("message-2");
        assertThat(root.path("result").has("message")).isTrue();
        assertThat(root.path("result").has("id"))
                .as("SendMessage returns proto SendMessageResponse oneof payload, not a naked Message")
                .isFalse();

        SendMessageResponse parsed = (SendMessageResponse) JSONRPCUtils.parseResponseBody(json, "SendMessage");
        assertThat(((Message) parsed.getResult()).messageId()).isEqualTo("message-2");
    }

    @Test
    void midStreamFailureEndsWithJsonRpcErrorFrame() throws Exception {
        A2aJsonRpcController controller = new A2aJsonRpcController(new FailAfterFirstEventRequestHandler(), new RuntimeAccessProperties());
        A2ARequest<?> request = JSONRPCUtils.parseRequestBody("""
                {"jsonrpc":"2.0","id":"stream-1","method":"SendStreamingMessage","params":{"message":{"role":"ROLE_USER","parts":[{"text":"ping"}],"messageId":"message-1"}}}
                """, null);

        StepVerifier.create(controller.handleStream(request, null))
                .assertNext(event -> assertThat(sdkParsedPayload(event)).isEqualTo(StreamResponse.PayloadCase.MESSAGE))
                .assertNext(event -> {
                    JsonNode root = dataJson(event);
                    assertThat(root.path("error").path("code").asInt())
                            .isEqualTo(new InternalError("boom").getCode());
                    assertThat(root.path("id").asText()).isEqualTo("stream-1");
                })
                .verifyComplete();
    }

    @Test
    void sendStreamingMessageCompletesCurrentResponseOnInputRequired() throws Exception {
        A2aJsonRpcController controller =
                new A2aJsonRpcController(new InputRequiredThenCompletedRequestHandler(), new RuntimeAccessProperties());
        A2ARequest<?> request = JSONRPCUtils.parseRequestBody("""
                {"jsonrpc":"2.0","id":"stream-input","method":"SendStreamingMessage","params":{"message":{"role":"ROLE_USER","parts":[{"text":"ping"}],"messageId":"message-1"}}}
                """, null);

        StepVerifier.create(controller.handleStream(request, null))
                .assertNext(event -> assertThat(statusState(event)).isEqualTo("TASK_STATE_WORKING"))
                .assertNext(event -> assertThat(statusState(event)).isEqualTo("TASK_STATE_INPUT_REQUIRED"))
                .verifyComplete();
    }

    @Test
    void subscribeToTaskKeepsStreamingAfterInputRequired() throws Exception {
        A2aJsonRpcController controller =
                new A2aJsonRpcController(new InputRequiredThenCompletedRequestHandler(), new RuntimeAccessProperties());
        A2ARequest<?> request = JSONRPCUtils.parseRequestBody("""
                {"jsonrpc":"2.0","id":"sub-input","method":"SubscribeToTask","params":{"id":"task-1"}}
                """, null);

        StepVerifier.create(controller.handleStream(request, null))
                .assertNext(event -> assertThat(statusState(event)).isEqualTo("TASK_STATE_WORKING"))
                .assertNext(event -> assertThat(statusState(event)).isEqualTo("TASK_STATE_INPUT_REQUIRED"))
                .assertNext(event -> assertThat(statusState(event)).isEqualTo("TASK_STATE_COMPLETED"))
                .verifyComplete();
    }

    @Test
    void sseParseFailureAnswersSingleErrorFrame() {
        A2aJsonRpcController controller = new A2aJsonRpcController(new SingleEventRequestHandler(), new RuntimeAccessProperties());

        StepVerifier.create(controller.handleSse("{not json", null))
                .assertNext(event -> assertThat(dataJson(event).path("error").path("code").asInt())
                        .isEqualTo(new JSONParseError().getCode()))
                .verifyComplete();
    }

    @Test
    void sseSynchronousA2aErrorAnswersSingleErrorFrame() {
        A2aJsonRpcController controller = new A2aJsonRpcController(new FailingRequestHandler(), new RuntimeAccessProperties());

        StepVerifier.create(controller.handleSse("""
                {"jsonrpc":"2.0","id":"sub-1","method":"SubscribeToTask","params":{"id":"missing-task"}}
                """, null))
                .assertNext(event -> {
                    JsonNode root = dataJson(event);
                    assertThat(root.path("error").path("code").asInt()).isEqualTo(new TaskNotFoundError().getCode());
                    assertThat(root.path("id").asText()).isEqualTo("sub-1");
                })
                .verifyComplete();
    }

    private static JsonNode blockingResponseJson(Object result) {
        assertThat(result).isInstanceOf(ResponseEntity.class);
        Object body = ((ResponseEntity<?>) result).getBody();
        assertThat(body).isInstanceOf(String.class);
        return readTree((String) body);
    }

    private static JsonNode dataJson(ServerSentEvent<String> event) {
        assertThat(event.event()).isEqualTo("jsonrpc");
        return readTree(event.data());
    }

    private static JsonNode readTree(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Response body is not valid JSON: " + json, e);
        }
    }

    private static StreamResponse.PayloadCase sdkParsedPayload(ServerSentEvent<String> event) {
        assertThat(event.event()).isEqualTo("jsonrpc");
        assertThat(event.data()).isNotBlank();
        try {
            return JSONRPCUtils.parseResponseEvent(event.data()).getPayloadCase();
        } catch (Exception e) {
            throw new AssertionError("SSE data is not parseable by the A2A SDK client", e);
        }
    }

    private static String statusState(ServerSentEvent<String> event) {
        return dataJson(event).path("result").path("statusUpdate").path("status").path("state").asText();
    }

    private static final class FailingRequestHandler extends SingleEventRequestHandler {
        @Override
        public Task onGetTask(TaskQueryParams params, ServerCallContext context) throws A2AError {
            throw new TaskNotFoundError();
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params, ServerCallContext context) {
            throw new TaskNotFoundError();
        }
    }

    private static final class ListingRequestHandler extends SingleEventRequestHandler {
        private final AtomicBoolean listed = new AtomicBoolean();
        private final AtomicReference<ServerCallContext> lastContext = new AtomicReference<>();

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult onListTasks(
                ListTasksParams params, ServerCallContext context) {
            listed.set(true);
            lastContext.set(context);
            return new org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult(List.of());
        }
    }

    private static final class TaskLookupRequestHandler extends SingleEventRequestHandler {
        @Override
        public Task onGetTask(TaskQueryParams params, ServerCallContext context) {
            return task(params.id());
        }

        @Override
        public Task onCancelTask(CancelTaskParams params, ServerCallContext context) {
            return task(params.id());
        }

        private static Task task(String id) {
            return Task.builder()
                    .id(id)
                    .contextId("ctx-1")
                    .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                    .metadata(Map.of("source", "test"))
                    .build();
        }
    }

    private static final class BlockingMessageRequestHandler extends SingleEventRequestHandler {
        @Override
        public EventKind onMessageSend(MessageSendParams params, ServerCallContext context) {
            return Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .messageId("message-2")
                    .parts(List.<Part<?>>of(new TextPart("pong")))
                    .build();
        }
    }

    private static final class FailAfterFirstEventRequestHandler extends SingleEventRequestHandler {
        @Override
        public Flow.Publisher<StreamingEventKind> onMessageSendStream(
                MessageSendParams params, ServerCallContext context) {
            Message message = Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .messageId("message-2")
                    .parts(List.<Part<?>>of(new TextPart("pong")))
                    .build();
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                subscriber.onNext(message);
                subscriber.onError(new RuntimeException("boom"));
            };
        }
    }

    private static final class InputRequiredThenCompletedRequestHandler extends SingleEventRequestHandler {
        @Override
        public Flow.Publisher<StreamingEventKind> onMessageSendStream(
                MessageSendParams params, ServerCallContext context) {
            return statusPublisher();
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params, ServerCallContext context) {
            return statusPublisher();
        }

        private static Flow.Publisher<StreamingEventKind> statusPublisher() {
            List<StreamingEventKind> events = List.of(
                    status(TaskState.TASK_STATE_WORKING),
                    status(TaskState.TASK_STATE_INPUT_REQUIRED),
                    status(TaskState.TASK_STATE_COMPLETED));
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                events.forEach(subscriber::onNext);
                subscriber.onComplete();
            };
        }

        private static TaskStatusUpdateEvent status(TaskState state) {
            return TaskStatusUpdateEvent.builder()
                    .taskId("task-1")
                    .contextId("ctx-1")
                    .status(new TaskStatus(state))
                    .build();
        }
    }

    private static class SingleEventRequestHandler implements RequestHandler {
        @Override
        public Flow.Publisher<StreamingEventKind> onMessageSendStream(
                MessageSendParams params, ServerCallContext context) {
            Message message = Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .messageId("message-2")
                    .parts(List.<Part<?>>of(new TextPart("pong")))
                    .metadata(Map.of("runStatus", "completed"))
                    .build();
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                subscriber.onNext(message);
                subscriber.onComplete();
            };
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params, ServerCallContext context) {
            return onMessageSendStream(null, context);
        }

        @Override
        public Task onGetTask(TaskQueryParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult onListTasks(
                ListTasksParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public Task onCancelTask(CancelTaskParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public EventKind onMessageSend(MessageSendParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public TaskPushNotificationConfig onCreateTaskPushNotificationConfig(
                TaskPushNotificationConfig params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public TaskPushNotificationConfig onGetTaskPushNotificationConfig(
                org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams params,
                ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public ListTaskPushNotificationConfigsResult onListTaskPushNotificationConfigs(
                ListTaskPushNotificationConfigsParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public void onDeleteTaskPushNotificationConfig(
                org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams params,
                ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public void validateRequestedTask(String requestedTaskId) throws A2AError {
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by this test");
        }
    }

    private static final class RecordingPushRequestHandler implements RequestHandler {
        private final AtomicReference<TaskPushNotificationConfig> lastCreated = new AtomicReference<>();
        private final AtomicReference<GetTaskPushNotificationConfigParams> lastGet = new AtomicReference<>();
        private final AtomicReference<ListTaskPushNotificationConfigsParams> lastList = new AtomicReference<>();
        private final AtomicReference<DeleteTaskPushNotificationConfigParams> lastDelete = new AtomicReference<>();

        @Override
        public TaskPushNotificationConfig onCreateTaskPushNotificationConfig(
                TaskPushNotificationConfig params, ServerCallContext context) {
            lastCreated.set(params);
            return params;
        }

        @Override
        public TaskPushNotificationConfig onGetTaskPushNotificationConfig(
                GetTaskPushNotificationConfigParams params, ServerCallContext context) {
            lastGet.set(params);
            return new TaskPushNotificationConfig(
                    params.id(), params.taskId(), "http://localhost:19090/a2a/push", null, null, null);
        }

        @Override
        public ListTaskPushNotificationConfigsResult onListTaskPushNotificationConfigs(
                ListTaskPushNotificationConfigsParams params, ServerCallContext context) {
            lastList.set(params);
            return new ListTaskPushNotificationConfigsResult(List.of(), null);
        }

        @Override
        public void onDeleteTaskPushNotificationConfig(
                DeleteTaskPushNotificationConfigParams params, ServerCallContext context) {
            lastDelete.set(params);
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onMessageSendStream(
                MessageSendParams params, ServerCallContext context) {
            throw unsupported();
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params, ServerCallContext context) {
            throw unsupported();
        }

        @Override
        public Task onGetTask(TaskQueryParams params, ServerCallContext context) {
            throw unsupported();
        }

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult onListTasks(
                ListTasksParams params, ServerCallContext context) {
            throw unsupported();
        }

        @Override
        public Task onCancelTask(CancelTaskParams params, ServerCallContext context) {
            throw unsupported();
        }

        @Override
        public EventKind onMessageSend(MessageSendParams params, ServerCallContext context) {
            throw unsupported();
        }

        @Override
        public void validateRequestedTask(String requestedTaskId) {
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by this test");
        }
    }
}
